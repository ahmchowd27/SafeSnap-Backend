package com.safesnap.backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.safesnap.backend.entity.IncidentCategory
import com.safesnap.backend.exception.RateLimitExceededException
import com.safesnap.backend.exception.OpenAiServiceException
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@Service
class ClaudeService(
    @Value("\${anthropic.api.key}") private val apiKey: String,
    @Value("\${anthropic.model:claude-sonnet-4-6}") private val model: String,
    @Value("\${anthropic.max-tokens:4096}") private val maxTokens: Int,
    @Value("\${anthropic.rate-limit.requests-per-minute:50}") private val requestsPerMinute: Int,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    private val metricsService: MetricsService
) {
    private val logger = LoggerFactory.getLogger(ClaudeService::class.java)

    private val requestBucket: Bucket = Bucket.builder()
        .addLimit(Bandwidth.classic(requestsPerMinute.toLong(), Refill.intervally(requestsPerMinute.toLong(), Duration.ofMinutes(1))))
        .build()

    private val userRequestBuckets = ConcurrentHashMap<String, Bucket>()

    companion object {
        private const val ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val ANTHROPIC_BETA = "prompt-caching-2024-07-31"

        // Cached system prompt — charged once, then read at ~10% cost on subsequent calls
        private val SYSTEM_PROMPT = """
            You are an expert OSHA safety inspector and root cause analysis specialist with 20+ years of experience
            in construction and warehouse safety compliance. You have authoritative knowledge of:

            - OSHA 29 CFR 1910 (General Industry Standards) and 29 CFR 1926 (Construction Standards)
            - NIOSH guidelines, ANSI Z10, and ISO 45001 occupational health and safety standards
            - Personal Protective Equipment (PPE) requirements by task and environment
            - Hazard identification, risk assessment, and hierarchy of controls
            - Root Cause Analysis (RCA) methodology including the Five Whys technique
            - OSHA recordkeeping requirements (29 CFR 1904) and incident classification
            - Common incident patterns: falls, struck-by, caught-in/between, electrocution
            - Corrective and preventive action (CAPA) frameworks

            When analyzing incidents you are precise, cite specific regulatory standards, identify systemic root
            causes rather than blaming individuals, and provide actionable CAPA that address both immediate hazards
            and underlying management system failures.
        """.trimIndent()

        // Tool schema for image analysis
        private val IMAGE_ANALYSIS_TOOL = mapOf(
            "name" to "submit_image_analysis",
            "description" to "Submit the structured safety analysis of the incident images",
            "input_schema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "summary" to mapOf(
                        "type" to "string",
                        "description" to "Detailed narrative description of what is observed in the images, focusing on safety-relevant conditions, hazards, and worker behavior"
                    ),
                    "safetyTags" to mapOf(
                        "type" to "array",
                        "items" to mapOf("type" to "string"),
                        "description" to "Safety-relevant keywords and hazard identifiers from the images (e.g. 'unsecured ladder', 'missing hard hat', 'wet floor')"
                    ),
                    "oshaViolations" to mapOf(
                        "type" to "array",
                        "items" to mapOf("type" to "string"),
                        "description" to "Specific OSHA regulation citations observed to be violated (e.g. '29 CFR 1926.1053(b)(1) - Ladder not secured')"
                    ),
                    "incidentCategory" to mapOf(
                        "type" to "string",
                        "enum" to listOf("PPE_VIOLATION", "EQUIPMENT_MALFUNCTION", "SLIP_TRIP_FALL", "LIFTING_INJURY",
                            "CHEMICAL_EXPOSURE", "ELECTRICAL_INCIDENT", "VEHICLE_INCIDENT", "FIRE_EXPLOSION",
                            "CONFINED_SPACE", "GENERAL_SAFETY"),
                        "description" to "Primary category of the incident based on visual and textual evidence"
                    ),
                    "textDetected" to mapOf(
                        "type" to "string",
                        "description" to "Any text visible in the images such as warning signs, labels, or safety markings"
                    ),
                    "confidenceScore" to mapOf(
                        "type" to "number",
                        "minimum" to 0.0,
                        "maximum" to 1.0,
                        "description" to "Confidence in the analysis accuracy based on image quality and clarity"
                    )
                ),
                "required" to listOf("summary", "safetyTags", "oshaViolations", "incidentCategory", "confidenceScore")
            )
        )

        // Tool schema for RCA generation
        private val RCA_GENERATION_TOOL = mapOf(
            "name" to "submit_rca_analysis",
            "description" to "Submit the structured root cause analysis for the safety incident",
            "input_schema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "fiveWhys" to mapOf(
                        "type" to "array",
                        "items" to mapOf("type" to "string"),
                        "minItems" to 5,
                        "maxItems" to 5,
                        "description" to "Exactly 5 sequential Why questions and answers tracing back to the root cause"
                    ),
                    "immediateCorrectiveActions" to mapOf(
                        "type" to "array",
                        "items" to mapOf("type" to "string"),
                        "description" to "Actions to take within 24-48 hours to address the immediate hazard"
                    ),
                    "longTermPreventiveActions" to mapOf(
                        "type" to "array",
                        "items" to mapOf("type" to "string"),
                        "description" to "Systemic changes to prevent recurrence within 30-90 days"
                    ),
                    "oshaReferences" to mapOf(
                        "type" to "array",
                        "items" to mapOf("type" to "string"),
                        "description" to "Specific OSHA standards applicable to this incident and corrective measures"
                    ),
                    "incidentCategory" to mapOf(
                        "type" to "string",
                        "enum" to listOf("PPE_VIOLATION", "EQUIPMENT_MALFUNCTION", "SLIP_TRIP_FALL", "LIFTING_INJURY",
                            "CHEMICAL_EXPOSURE", "ELECTRICAL_INCIDENT", "VEHICLE_INCIDENT", "FIRE_EXPLOSION",
                            "CONFINED_SPACE", "GENERAL_SAFETY")
                    ),
                    "confidenceScore" to mapOf(
                        "type" to "number",
                        "minimum" to 0.0,
                        "maximum" to 1.0
                    )
                ),
                "required" to listOf("fiveWhys", "immediateCorrectiveActions", "longTermPreventiveActions",
                    "oshaReferences", "incidentCategory", "confidenceScore")
            )
        )
    }

    /**
     * Analyze incident images using Claude's vision capabilities.
     * Returns a structured safety analysis including OSHA violations, tags, and incident category.
     */
    fun analyzeImages(
        imageDataList: List<Pair<String, ByteArray>>, // (mediaType, bytes)
        incidentTitle: String,
        incidentDescription: String,
        severity: String,
        locationDescription: String?,
        userEmail: String? = null
    ): ImageAnalysisResult {
        checkRateLimits(userEmail)

        return metricsService.timeImageProcessing {
            try {
                logger.info("Analyzing ${imageDataList.size} images with Claude Vision for incident: $incidentTitle")

                val contentBlocks = mutableListOf<Map<String, Any>>()

                // Add each image as a base64-encoded content block
                imageDataList.forEach { (mediaType, bytes) ->
                    contentBlocks.add(mapOf(
                        "type" to "image",
                        "source" to mapOf(
                            "type" to "base64",
                            "media_type" to mediaType,
                            "data" to Base64.getEncoder().encodeToString(bytes)
                        )
                    ))
                }

                contentBlocks.add(mapOf(
                    "type" to "text",
                    "text" to buildImageAnalysisPrompt(incidentTitle, incidentDescription, severity, locationDescription)
                ))

                val response = callClaude(
                    contentBlocks = contentBlocks,
                    toolName = "submit_image_analysis",
                    tool = IMAGE_ANALYSIS_TOOL
                )

                metricsService.recordVisionApiCall()

                val input = response.toolInput
                ImageAnalysisResult(
                    success = true,
                    summary = input["summary"] as String,
                    safetyTags = (input["safetyTags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    oshaViolations = (input["oshaViolations"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    incidentCategory = parseCategory(input["incidentCategory"] as? String),
                    textDetected = input["textDetected"] as? String,
                    confidenceScore = (input["confidenceScore"] as? Number)?.toDouble() ?: 0.0,
                    tokensUsed = response.tokensUsed,
                    errorMessage = null
                )

            } catch (e: Exception) {
                logger.error("Failed to analyze images with Claude", e)
                ImageAnalysisResult(
                    success = false,
                    summary = "",
                    safetyTags = emptyList(),
                    oshaViolations = emptyList(),
                    incidentCategory = IncidentCategory.GENERAL_SAFETY,
                    textDetected = null,
                    confidenceScore = 0.0,
                    tokensUsed = 0,
                    errorMessage = "Image analysis failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Generate a structured RCA using Claude, based on image analysis results and incident details.
     */
    fun generateRca(
        incidentTitle: String,
        incidentDescription: String,
        severity: String,
        locationDescription: String?,
        reporterName: String,
        reporterRole: String,
        imageAnalysisSummaries: List<String>,
        safetyTags: List<String>,
        oshaViolationsFromImages: List<String>,
        incidentCategory: IncidentCategory,
        userEmail: String? = null
    ): RcaAnalysisResult {
        checkRateLimits(userEmail)

        return metricsService.timeOpenAiRequest {
            try {
                logger.info("Generating RCA with Claude for incident: $incidentTitle (category: $incidentCategory)")

                val contentBlocks = listOf(
                    mapOf(
                        "type" to "text",
                        "text" to buildRcaPrompt(
                            incidentTitle, incidentDescription, severity, locationDescription,
                            reporterName, reporterRole, imageAnalysisSummaries,
                            safetyTags, oshaViolationsFromImages, incidentCategory
                        )
                    )
                )

                val response = callClaude(
                    contentBlocks = contentBlocks,
                    toolName = "submit_rca_analysis",
                    tool = RCA_GENERATION_TOOL
                )

                metricsService.recordOpenAiSuccess()

                val input = response.toolInput
                RcaAnalysisResult(
                    success = true,
                    fiveWhys = (input["fiveWhys"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    immediateCorrectiveActions = (input["immediateCorrectiveActions"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    longTermPreventiveActions = (input["longTermPreventiveActions"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    oshaReferences = (input["oshaReferences"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    incidentCategory = parseCategory(input["incidentCategory"] as? String),
                    confidenceScore = (input["confidenceScore"] as? Number)?.toDouble() ?: 0.0,
                    tokensUsed = response.tokensUsed,
                    errorMessage = null
                )

            } catch (e: Exception) {
                logger.error("Failed to generate RCA with Claude", e)
                metricsService.recordOpenAiError("rca_generation_failed")
                RcaAnalysisResult(
                    success = false,
                    fiveWhys = emptyList(),
                    immediateCorrectiveActions = emptyList(),
                    longTermPreventiveActions = emptyList(),
                    oshaReferences = emptyList(),
                    incidentCategory = incidentCategory,
                    confidenceScore = 0.0,
                    tokensUsed = 0,
                    errorMessage = "RCA generation failed: ${e.message}"
                )
            }
        }
    }

    fun healthCheck(): Boolean = apiKey.isNotBlank()

    private fun callClaude(
        contentBlocks: List<Map<String, Any>>,
        toolName: String,
        tool: Map<String, Any>
    ): ClaudeToolResponse {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("x-api-key", apiKey)
            set("anthropic-version", ANTHROPIC_VERSION)
            set("anthropic-beta", ANTHROPIC_BETA)
        }

        val body = mapOf(
            "model" to model,
            "max_tokens" to maxTokens,
            "system" to listOf(
                mapOf(
                    "type" to "text",
                    "text" to SYSTEM_PROMPT,
                    "cache_control" to mapOf("type" to "ephemeral") // prompt caching
                )
            ),
            "tools" to listOf(tool),
            "tool_choice" to mapOf("type" to "tool", "name" to toolName),
            "messages" to listOf(
                mapOf("role" to "user", "content" to contentBlocks)
            )
        )

        try {
            val response = restTemplate.postForEntity(ANTHROPIC_API_URL, HttpEntity(body, headers), String::class.java)

            if (response.statusCode != HttpStatus.OK) {
                throw OpenAiServiceException("Claude API returned status: ${response.statusCode}")
            }

            return parseClaudeResponse(response.body ?: throw OpenAiServiceException("Empty response from Claude"))

        } catch (e: HttpClientErrorException) {
            when (e.statusCode) {
                HttpStatus.TOO_MANY_REQUESTS -> throw RateLimitExceededException("Claude rate limit exceeded")
                HttpStatus.UNAUTHORIZED -> throw OpenAiServiceException("Invalid Anthropic API key")
                else -> throw OpenAiServiceException("Claude API error ${e.statusCode}: ${e.responseBodyAsString}")
            }
        } catch (e: HttpServerErrorException) {
            throw OpenAiServiceException("Claude service unavailable: ${e.message}")
        }
    }

    private fun parseClaudeResponse(responseBody: String): ClaudeToolResponse {
        val json = objectMapper.readTree(responseBody)

        val usage = json.get("usage")
        val inputTokens = usage?.get("input_tokens")?.asInt() ?: 0
        val outputTokens = usage?.get("output_tokens")?.asInt() ?: 0
        val cacheReadTokens = usage?.get("cache_read_input_tokens")?.asInt() ?: 0

        logger.debug("Claude token usage — input: $inputTokens, output: $outputTokens, cache_read: $cacheReadTokens")

        val content = json.get("content") ?: throw OpenAiServiceException("No content in Claude response")
        val toolUse = content.firstOrNull { it.get("type")?.asText() == "tool_use" }
            ?: throw OpenAiServiceException("Claude did not call the expected tool")

        val toolInput = objectMapper.convertValue(toolUse.get("input"), Map::class.java)
            ?: throw OpenAiServiceException("Empty tool input from Claude")

        @Suppress("UNCHECKED_CAST")
        return ClaudeToolResponse(
            toolInput = toolInput as Map<String, Any>,
            tokensUsed = inputTokens + outputTokens
        )
    }

    private fun checkRateLimits(userEmail: String?) {
        if (!requestBucket.tryConsume(1)) {
            throw RateLimitExceededException("Claude service rate limit exceeded. Please try again later.")
        }
        userEmail?.let { email ->
            val userBucket = userRequestBuckets.computeIfAbsent(email) {
                Bucket.builder()
                    .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
                    .build()
            }
            if (!userBucket.tryConsume(1)) {
                throw RateLimitExceededException("User rate limit exceeded. Please try again later.")
            }
        }
    }

    private fun parseCategory(value: String?): IncidentCategory {
        return try {
            IncidentCategory.valueOf(value ?: "GENERAL_SAFETY")
        } catch (e: IllegalArgumentException) {
            IncidentCategory.GENERAL_SAFETY
        }
    }

    private fun buildImageAnalysisPrompt(
        title: String,
        description: String,
        severity: String,
        location: String?
    ): String = """
        Analyze the attached incident image(s) for safety violations and hazards.

        Incident Details:
        - Title: $title
        - Description: $description
        - Severity: $severity
        - Location: ${location ?: "Not specified"}

        Examine each image carefully for:
        1. PPE compliance (hard hats, hi-vis vests, safety glasses, gloves, harnesses, steel-toed boots)
        2. Equipment condition and proper use
        3. Environmental hazards (wet floors, poor lighting, blocked exits, unstable surfaces)
        4. Fall protection and working at height risks
        5. Electrical, chemical, or fire hazards
        6. Housekeeping and workspace organization issues
        7. Any visible warning signs, labels, or safety markings

        Cite specific OSHA standards where violations are apparent.
        Use the submit_image_analysis tool to submit your findings.
    """.trimIndent()

    private fun buildRcaPrompt(
        title: String,
        description: String,
        severity: String,
        location: String?,
        reporterName: String,
        reporterRole: String,
        imageAnalysisSummaries: List<String>,
        safetyTags: List<String>,
        oshaViolationsFromImages: List<String>,
        category: IncidentCategory
    ): String {
        val imageSection = if (imageAnalysisSummaries.isNotEmpty()) {
            "\nImage Analysis Findings:\n" + imageAnalysisSummaries.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
        } else ""

        val violationsSection = if (oshaViolationsFromImages.isNotEmpty()) {
            "\nOSHA Violations Identified in Images:\n" + oshaViolationsFromImages.joinToString("\n") { "- $it" }
        } else ""

        val tagsSection = if (safetyTags.isNotEmpty()) {
            "\nSafety Hazard Tags: ${safetyTags.joinToString(", ")}"
        } else ""

        return """
            Generate a comprehensive Root Cause Analysis for the following workplace safety incident.

            Incident Details:
            - Title: $title
            - Description: $description
            - Severity: $severity
            - Location: ${location ?: "Not specified"}
            - Reported by: $reporterName ($reporterRole)
            - Category: ${category.name.replace("_", " ")}
            $imageSection
            $violationsSection
            $tagsSection

            Instructions:
            1. Five Whys: Provide exactly 5 sequential why questions that drill from the surface event to the
               systemic root cause. Each entry should be a complete "Why X? Because Y" statement.
            2. Immediate Corrective Actions: List 3-5 actions to take within 24-48 hours to eliminate or
               control the immediate hazard.
            3. Long-term Preventive Actions: List 3-5 systemic changes (training, procedures, engineering
               controls, management systems) to prevent recurrence within 30-90 days.
            4. OSHA References: Cite specific applicable standards with section numbers.

            Focus on systemic and organizational root causes, not individual blame.
            Use the submit_rca_analysis tool to submit your analysis.
        """.trimIndent()
    }
}

// Internal response wrapper from Claude tool call
private data class ClaudeToolResponse(
    val toolInput: Map<String, Any>,
    val tokensUsed: Int
)

// Result of Claude image analysis
data class ImageAnalysisResult(
    val success: Boolean,
    val summary: String,
    val safetyTags: List<String>,
    val oshaViolations: List<String>,
    val incidentCategory: IncidentCategory,
    val textDetected: String?,
    val confidenceScore: Double,
    val tokensUsed: Int,
    val errorMessage: String?
)

// Result of Claude RCA generation
data class RcaAnalysisResult(
    val success: Boolean,
    val fiveWhys: List<String>,
    val immediateCorrectiveActions: List<String>,
    val longTermPreventiveActions: List<String>,
    val oshaReferences: List<String>,
    val incidentCategory: IncidentCategory,
    val confidenceScore: Double,
    val tokensUsed: Int,
    val errorMessage: String?
)
