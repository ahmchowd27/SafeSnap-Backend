package com.safesnap.backend.service

import com.safesnap.backend.entity.IncidentCategory
import com.safesnap.backend.entity.Incident
import com.safesnap.backend.entity.ImageAnalysis
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class IncidentCategorizationService {
    
    private val logger = LoggerFactory.getLogger(IncidentCategorizationService::class.java)
    
    /**
     * Categorize incident based on description, title, and image analysis
     */
    fun categorizeIncident(
        incident: Incident,
        imageAnalyses: List<ImageAnalysis> = emptyList()
    ): IncidentCategory {
        
        logger.info("Categorizing incident: ${incident.id}")
        
        val text = "${incident.title} ${incident.description}".lowercase()
        val safetyTags = imageAnalyses
            .filter { it.processed }
            .flatMap { it.tags.split(",").map { tag -> tag.trim().lowercase() } }
        
        logger.debug("Text analysis input: ${text.take(100)}...")
        logger.debug("Safety tags from images: $safetyTags")
        
        val category = when {
            // PPE Violations - check text and image tags
            isPpeViolation(text, safetyTags) -> IncidentCategory.PPE_VIOLATION
            
            // Equipment Malfunction
            isEquipmentMalfunction(text, safetyTags) -> IncidentCategory.EQUIPMENT_MALFUNCTION
            
            // Slip, Trip, Fall
            isSlipTripFall(text, safetyTags) -> IncidentCategory.SLIP_TRIP_FALL
            
            // Lifting Injury
            isLiftingInjury(text, safetyTags) -> IncidentCategory.LIFTING_INJURY
            
            // Chemical Exposure
            isChemicalExposure(text, safetyTags) -> IncidentCategory.CHEMICAL_EXPOSURE
            
            // Electrical Incident
            isElectricalIncident(text, safetyTags) -> IncidentCategory.ELECTRICAL_INCIDENT
            
            // Vehicle Incident
            isVehicleIncident(text, safetyTags) -> IncidentCategory.VEHICLE_INCIDENT
            
            // Fire/Explosion
            isFireExplosion(text, safetyTags) -> IncidentCategory.FIRE_EXPLOSION
            
            // Confined Space
            isConfinedSpace(text, safetyTags) -> IncidentCategory.CONFINED_SPACE
            
            // Default to General Safety
            else -> IncidentCategory.GENERAL_SAFETY
        }
        
        logger.info("Incident ${incident.id} categorized as: $category")
        return category
    }
    
    private fun isPpeViolation(text: String, tags: List<String>): Boolean {
        val ppeKeywords = listOf(
            "hard hat", "helmet", "safety vest", "safety glasses", "gloves", "boots",
            "harness", "ppe", "personal protective equipment", "no helmet", "no hard hat",
            "missing vest", "not wearing", "forgot", "left behind", "without protection"
        )
        
        val ppeImageTags = listOf(
            "hard hat", "helmet", "safety vest", "protective equipment", "safety gear"
        )
        
        return containsKeywords(text, ppeKeywords) || 
               containsKeywords(tags.joinToString(" "), ppeImageTags) ||
               text.contains("ppe") || text.contains("protective equipment")
    }
    
    private fun isEquipmentMalfunction(text: String, tags: List<String>): Boolean {
        val equipmentKeywords = listOf(
            "malfunction", "broken", "defective", "failure", "not working",
            "machine", "equipment", "tool", "crane", "forklift", "excavator",
            "bulldozer", "drill", "saw", "grinder", "compressor", "generator",
            "conveyor", "pump", "motor", "engine", "hydraulic", "mechanical"
        )
        
        return containsKeywords(text, equipmentKeywords) ||
               tags.any { it.contains("machinery") || it.contains("equipment") || it.contains("tool") }
    }
    
    private fun isSlipTripFall(text: String, tags: List<String>): Boolean {
        val fallKeywords = listOf(
            "slip", "slipped", "trip", "tripped", "fall", "fell", "falling",
            "wet floor", "spill", "leak", "slippery", "stumble", "ice",
            "ladder", "stairs", "platform", "elevation", "height", "dropped"
        )
        
        return containsKeywords(text, fallKeywords) ||
               tags.any { it.contains("ladder") || it.contains("stairs") || it.contains("platform") }
    }
    
    private fun isLiftingInjury(text: String, @Suppress("UNUSED_PARAMETER") tags: List<String>): Boolean {
        val liftingKeywords = listOf(
            "lifting", "lifted", "carrying", "moving", "heavy", "strain",
            "back injury", "pulled muscle", "herniated", "manual handling",
            "repetitive", "ergonomic", "posture", "overexertion", "twist"
        )
        
        return containsKeywords(text, liftingKeywords)
    }
    
    private fun isChemicalExposure(text: String, @Suppress("UNUSED_PARAMETER") tags: List<String>): Boolean {
        val chemicalKeywords = listOf(
            "chemical", "toxic", "hazardous material", "spill", "leak", "fumes",
            "vapor", "gas", "acid", "base", "solvent", "paint", "adhesive",
            "exposure", "inhaled", "skin contact", "eye contact", "msds", "sds"
        )
        
        return containsKeywords(text, chemicalKeywords)
    }
    
    private fun isElectricalIncident(text: String, tags: List<String>): Boolean {
        val electricalKeywords = listOf(
            "electrical", "electric", "shock", "electrocuted", "voltage", "current",
            "wire", "cable", "outlet", "panel", "breaker", "short circuit",
            "arc flash", "ground fault", "lockout", "tagout", "loto"
        )
        
        return containsKeywords(text, electricalKeywords) ||
               tags.any { it.contains("electrical") || it.contains("wire") || it.contains("cable") }
    }
    
    private fun isVehicleIncident(text: String, tags: List<String>): Boolean {
        val vehicleKeywords = listOf(
            "vehicle", "truck", "car", "forklift", "crane", "excavator", "bulldozer",
            "collision", "accident", "crash", "hit", "struck", "backed into",
            "mobile equipment", "heavy machinery", "operator", "driving"
        )
        
        return containsKeywords(text, vehicleKeywords) ||
               tags.any { it.contains("vehicle") || it.contains("truck") || it.contains("forklift") }
    }
    
    private fun isFireExplosion(text: String, tags: List<String>): Boolean {
        val fireKeywords = listOf(
            "fire", "flame", "burn", "burned", "explosion", "blast", "ignition",
            "combustible", "flammable", "smoke", "heat", "hot work", "welding",
            "cutting", "grinding", "spark", "overheating"
        )
        
        return containsKeywords(text, fireKeywords) ||
               tags.any { it.contains("fire") || it.contains("welding") || it.contains("cutting") }
    }
    
    private fun isConfinedSpace(text: String, @Suppress("UNUSED_PARAMETER") tags: List<String>): Boolean {
        val confinedSpaceKeywords = listOf(
            "confined space", "tank", "vessel", "pit", "trench", "sewer",
            "tunnel", "vault", "silo", "oxygen", "ventilation", "atmosphere",
            "entry permit", "attendant", "rescue"
        )
        
        return containsKeywords(text, confinedSpaceKeywords)
    }
    
    private fun containsKeywords(text: String, keywords: List<String>): Boolean {
        return keywords.any { keyword ->
            text.contains(keyword, ignoreCase = true)
        }
    }
    
    /**
     * Get confidence score for categorization (0.0 to 1.0)
     */
    fun getCategorizationConfidence(
        incident: Incident,
        category: IncidentCategory,
        imageAnalyses: List<ImageAnalysis> = emptyList()
    ): Double {
        
        val text = "${incident.title} ${incident.description}".lowercase()
        val safetyTags = imageAnalyses
            .filter { it.processed }
            .flatMap { it.tags.split(",").map { tag -> tag.trim().lowercase() } }
        
        // Count keyword matches for the assigned category
        val matchScore = when (category) {
            IncidentCategory.PPE_VIOLATION -> countPpeMatches(text, safetyTags)
            IncidentCategory.EQUIPMENT_MALFUNCTION -> countEquipmentMatches(text, safetyTags)
            IncidentCategory.SLIP_TRIP_FALL -> countFallMatches(text, safetyTags)
            IncidentCategory.LIFTING_INJURY -> countLiftingMatches(text, safetyTags)
            IncidentCategory.CHEMICAL_EXPOSURE -> countChemicalMatches(text, safetyTags)
            IncidentCategory.ELECTRICAL_INCIDENT -> countElectricalMatches(text, safetyTags)
            IncidentCategory.VEHICLE_INCIDENT -> countVehicleMatches(text, safetyTags)
            IncidentCategory.FIRE_EXPLOSION -> countFireMatches(text, safetyTags)
            IncidentCategory.CONFINED_SPACE -> countConfinedSpaceMatches(text, safetyTags)
            IncidentCategory.GENERAL_SAFETY -> 1 // Default low confidence
        }
        
        // Convert match count to confidence score (normalize to 0.0-1.0)
        return when {
            matchScore >= 3 -> 0.9
            matchScore == 2 -> 0.7
            matchScore == 1 -> 0.5
            else -> 0.3
        }
    }
    
    private fun countPpeMatches(text: String, tags: List<String>): Int {
        val ppeKeywords = listOf(
            "hard hat", "helmet", "safety vest", "safety glasses", "gloves", "boots",
            "ppe", "personal protective equipment", "no helmet", "not wearing"
        )
        return ppeKeywords.count { text.contains(it, ignoreCase = true) } +
               tags.count { it.contains("hard hat") || it.contains("helmet") || it.contains("safety vest") }
    }
    
    private fun countEquipmentMatches(text: String, tags: List<String>): Int {
        val keywords = listOf("malfunction", "broken", "equipment", "machine", "tool", "crane", "forklift")
        return keywords.count { text.contains(it, ignoreCase = true) } +
               tags.count { it.contains("machinery") || it.contains("equipment") }
    }
    
    private fun countFallMatches(text: String, tags: List<String>): Int {
        val keywords = listOf("slip", "trip", "fall", "ladder", "stairs", "wet floor", "height")
        return keywords.count { text.contains(it, ignoreCase = true) } +
               tags.count { it.contains("ladder") || it.contains("stairs") }
    }
    
    private fun countLiftingMatches(text: String, @Suppress("UNUSED_PARAMETER") tags: List<String>): Int {
        val keywords = listOf("lifting", "carrying", "heavy", "back injury", "strain", "manual handling")
        return keywords.count { text.contains(it, ignoreCase = true) }
    }
    
    private fun countChemicalMatches(text: String, @Suppress("UNUSED_PARAMETER") tags: List<String>): Int {
        val keywords = listOf("chemical", "toxic", "spill", "fumes", "exposure", "hazardous")
        return keywords.count { text.contains(it, ignoreCase = true) }
    }
    
    private fun countElectricalMatches(text: String, tags: List<String>): Int {
        val keywords = listOf("electrical", "shock", "voltage", "wire", "cable", "arc flash")
        return keywords.count { text.contains(it, ignoreCase = true) } +
               tags.count { it.contains("electrical") || it.contains("wire") }
    }
    
    private fun countVehicleMatches(text: String, tags: List<String>): Int {
        val keywords = listOf("vehicle", "truck", "forklift", "collision", "accident", "struck")
        return keywords.count { text.contains(it, ignoreCase = true) } +
               tags.count { it.contains("vehicle") || it.contains("forklift") }
    }
    
    private fun countFireMatches(text: String, tags: List<String>): Int {
        val keywords = listOf("fire", "burn", "explosion", "welding", "spark", "heat")
        return keywords.count { text.contains(it, ignoreCase = true) } +
               tags.count { it.contains("fire") || it.contains("welding") }
    }
    
    private fun countConfinedSpaceMatches(text: String, @Suppress("UNUSED_PARAMETER") tags: List<String>): Int {
        val keywords = listOf("confined space", "tank", "pit", "trench", "ventilation", "atmosphere")
        return keywords.count { text.contains(it, ignoreCase = true) }
    }
}
