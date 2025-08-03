package com.safesnap.backend.service

import com.safesnap.backend.entity.IncidentCategory
import com.safesnap.backend.entity.Incident
import com.safesnap.backend.entity.ImageAnalysis
import org.springframework.stereotype.Service

@Service
class RcaTemplateService {
    
    /**
     * Get the appropriate RCA generation prompt template based on incident category
     */
    fun getTemplate(
        category: IncidentCategory,
        incident: Incident,
        imageAnalyses: List<ImageAnalysis>,
        reporterName: String,
        reporterRole: String
    ): String{
        val baseContext = buildIncidentContext(incident, imageAnalyses, reporterName, reporterRole)

        return when (category) {
            IncidentCategory.PPE_VIOLATION -> getPpeViolationTemplate(baseContext)
            IncidentCategory.EQUIPMENT_MALFUNCTION -> getEquipmentMalfunctionTemplate(baseContext)
            IncidentCategory.SLIP_TRIP_FALL -> getSlipTripFallTemplate(baseContext)
            IncidentCategory.LIFTING_INJURY -> getLiftingInjuryTemplate(baseContext)
            IncidentCategory.CHEMICAL_EXPOSURE -> getChemicalExposureTemplate(baseContext)
            IncidentCategory.ELECTRICAL_INCIDENT -> getElectricalIncidentTemplate(baseContext)
            IncidentCategory.VEHICLE_INCIDENT -> getVehicleIncidentTemplate(baseContext)
            IncidentCategory.FIRE_EXPLOSION -> getFireExplosionTemplate(baseContext)
            IncidentCategory.CONFINED_SPACE -> getConfinedSpaceTemplate(baseContext)
            IncidentCategory.GENERAL_SAFETY -> getGeneralSafetyTemplate(baseContext)
        }
    }

    private fun buildIncidentContext(
        incident: Incident,
        imageAnalyses: List<ImageAnalysis>,
        reporterFullName: String,
        reporterRole: String
    ): String {
        val safetyTags = imageAnalyses
            .filter { it.processed }
            .joinToString(", ") { it.tags }
            .takeIf { it.isNotBlank() } ?: "No image analysis available"

        val location = incident.locationDescription?.takeIf { it.isNotBlank() }
            ?: "Location not specified"

        return """
            INCIDENT DETAILS:
            - Title: ${incident.title}
            - Description: ${incident.description}
            - Severity: ${incident.severity}
            - Location: $location
            - Reported by: $reporterFullName ($reporterRole)
            - Date: ${incident.reportedAt}
            - Image Analysis Tags: $safetyTags
        """.trimIndent()
    }

    private fun getPpeViolationTemplate(context: String): String = """
        You are a safety expert analyzing a Personal Protective Equipment (PPE) violation incident. Generate a professional Root Cause Analysis following industry best practices for construction and warehouse environments.

        $context

        Generate EXACTLY this format with specific, actionable content:

        FIVE WHYS:
        1. Why did this PPE violation occur? [Focus on the immediate cause - missing equipment, not worn, etc.]
        2. Why was the required PPE not properly used? [Behavioral factors - forgot, uncomfortable, unavailable, etc.]
        3. Why wasn't this prevented by existing safety protocols? [System failures - inadequate checks, training gaps, etc.]
        4. Why aren't safety protocols being effectively implemented? [Management oversight - supervision, enforcement, resources]
        5. Why isn't there sufficient organizational commitment to PPE compliance? [Cultural/policy issues - priorities, accountability, safety culture]

        CORRECTIVE ACTIONS (Immediate - next 24-48 hours):
        - [Specific action to address immediate PPE need]
        - [Immediate safety measure for this worker/area]
        - [Documentation and notification requirement]
        - [Short-term supervision or monitoring change]

        PREVENTIVE ACTIONS (Long-term - next 30-90 days):
        - [System improvement for PPE availability/access]
        - [Training or education enhancement]
        - [Policy or procedure modification]
        - [Monitoring or accountability improvement]
        - [Cultural or management system change]

        Focus on construction/warehouse-specific PPE requirements (hard hats, safety vests, steel-toed boots, safety glasses, gloves, harnesses). Be specific about actionable steps that can realistically be implemented.
    """.trimIndent()
    
    private fun getEquipmentMalfunctionTemplate(context: String): String = """
        You are a safety expert analyzing an equipment malfunction incident. Generate a professional Root Cause Analysis focusing on mechanical systems, maintenance, and operational safety.

        $context

        Generate EXACTLY this format:

        FIVE WHYS:
        1. Why did this equipment malfunction occur? [Immediate mechanical/technical cause]
        2. Why did the equipment fail in this way? [Underlying mechanical issues - wear, damage, misuse]
        3. Why wasn't this failure prevented by maintenance? [Maintenance system gaps - scheduling, procedures, detection]
        4. Why isn't the maintenance system more effective? [Management system issues - resources, training, oversight]
        5. Why isn't there better organizational commitment to equipment reliability? [Strategic/cultural issues]

        CORRECTIVE ACTIONS (Immediate - next 24-48 hours):
        - [Immediate equipment isolation/repair action]
        - [Safety assessment of similar equipment]
        - [Temporary alternative procedures if needed]
        - [Incident documentation and reporting]

        PREVENTIVE ACTIONS (Long-term - next 30-90 days):
        - [Maintenance schedule/procedure improvement]
        - [Equipment inspection enhancement]
        - [Operator training improvement]
        - [Replacement or upgrade consideration]
        - [Monitoring system enhancement]

        Focus on construction/warehouse equipment like forklifts, cranes, conveyor systems, power tools, and heavy machinery. Address both mechanical and operational safety aspects.
    """.trimIndent()
    
    private fun getSlipTripFallTemplate(context: String): String = """
        You are a safety expert analyzing a slip, trip, or fall incident. Generate a professional Root Cause Analysis focusing on surface conditions, housekeeping, and fall prevention.

        $context

        Generate EXACTLY this format:

        FIVE WHYS:
        1. Why did this slip/trip/fall occur? [Immediate surface/environmental cause]
        2. Why was the hazardous condition present? [Housekeeping, spills, obstacles, surface issues]
        3. Why wasn't this hazard identified and corrected? [Inspection, reporting, response system gaps]
        4. Why aren't hazard identification systems more effective? [Management systems, training, accountability]
        5. Why isn't there better organizational focus on fall prevention? [Safety culture, resource allocation, priorities]

        CORRECTIVE ACTIONS (Immediate - next 24-48 hours):
        - [Immediate area cleanup/correction]
        - [Hazard marking or barrier installation]
        - [Medical attention/injury assessment]
        - [Similar hazard inspection throughout facility]

        PREVENTIVE ACTIONS (Long-term - next 30-90 days):
        - [Housekeeping procedure improvement]
        - [Surface condition monitoring system]
        - [Fall prevention training enhancement]
        - [Environmental control improvement (lighting, drainage, etc.)]
        - [Personal fall protection equipment evaluation]

        Address both same-level falls (slips/trips) and elevation falls (ladders, platforms, stairs). Focus on construction and warehouse-specific hazards.
    """.trimIndent()
    
    private fun getLiftingInjuryTemplate(context: String): String = """
        You are a safety expert analyzing a lifting or manual handling injury. Generate a professional Root Cause Analysis focusing on ergonomics, technique, and injury prevention.

        $context

        Generate EXACTLY this format:

        FIVE WHYS:
        1. Why did this lifting injury occur? [Immediate cause - technique, load, position]
        2. Why was improper lifting technique used? [Training, equipment, job design factors]
        3. Why weren't ergonomic controls in place to prevent this? [Job design, equipment availability, assessment gaps]
        4. Why isn't the ergonomics program more effective? [Management commitment, resources, expertise]
        5. Why isn't there better organizational focus on injury prevention? [Safety culture, priorities, accountability]

        CORRECTIVE ACTIONS (Immediate - next 24-48 hours):
        - [Immediate medical attention/evaluation]
        - [Job task modification or restriction]
        - [Lifting aid or equipment provision]
        - [Similar task assessment throughout workplace]

        PREVENTIVE ACTIONS (Long-term - next 30-90 days):
        - [Manual handling training enhancement]
        - [Mechanical lifting aid implementation]
        - [Job design/workflow modification]
        - [Ergonomic assessment program implementation]
        - [Fitness-for-duty or pre-shift preparation program]

        Focus on construction and warehouse manual handling tasks including materials, equipment, and repetitive motions. Address both acute injuries and cumulative trauma prevention.
    """.trimIndent()
    
    private fun getChemicalExposureTemplate(context: String): String = """
        You are a safety expert analyzing a chemical exposure incident. Generate a professional Root Cause Analysis focusing on hazardous materials safety and exposure prevention.

        $context

        Generate EXACTLY this format:

        FIVE WHYS:
        1. Why did this chemical exposure occur? [Immediate exposure pathway - inhalation, skin contact, ingestion]
        2. Why were exposure controls inadequate? [PPE, ventilation, containment, procedure failures]
        3. Why weren't proper chemical safety measures in place? [Training, hazard communication, system gaps]
        4. Why isn't the chemical safety program more effective? [Management oversight, resources, compliance]
        5. Why isn't there stronger organizational commitment to chemical safety? [Safety culture, priorities, regulatory compliance]

        CORRECTIVE ACTIONS (Immediate - next 24-48 hours):
        - [Immediate medical evaluation/treatment]
        - [Chemical area isolation/decontamination]
        - [Emergency response procedure activation]
        - [Similar chemical hazard assessment]

        PREVENTIVE ACTIONS (Long-term - next 30-90 days):
        - [Chemical safety training enhancement]
        - [Personal protective equipment upgrade]
        - [Ventilation/engineering control improvement]
        - [Hazard communication program enhancement]
        - [Chemical handling procedure revision]

        Address construction chemicals (solvents, adhesives, paints) and warehouse materials (cleaning agents, fuels, industrial chemicals). Include regulatory compliance considerations.
    """.trimIndent()
    
    private fun getElectricalIncidentTemplate(context: String): String = """
        You are a safety expert analyzing an electrical incident. Generate a professional Root Cause Analysis focusing on electrical safety and shock prevention.

        $context

        Generate EXACTLY this format:

        FIVE WHYS:
        1. Why did this electrical incident occur? [Immediate electrical cause - contact, arc, fault]
        2. Why was the electrical hazard present? [Equipment condition, installation, maintenance issues]
        3. Why weren't electrical safety controls effective? [Lockout/tagout, guarding, procedure gaps]
        4. Why isn't the electrical safety program more robust? [Training, maintenance, inspection systems]
        5. Why isn't there stronger organizational commitment to electrical safety? [Safety culture, qualified personnel, compliance]

        CORRECTIVE ACTIONS (Immediate - next 24-48 hours):
        - [Immediate medical attention for electrical injury]
        - [Electrical system isolation and inspection]
        - [Qualified electrician assessment]
        - [Similar electrical hazard survey]

        PREVENTIVE ACTIONS (Long-term - next 30-90 days):
        - [Electrical safety training enhancement]
        - [Lockout/tagout procedure improvement]
        - [Electrical maintenance program upgrade]
        - [Ground fault protection installation]
        - [Qualified electrical worker certification]

        Focus on construction electrical work and warehouse electrical systems. Address both qualified electrical worker safety and general worker protection from electrical hazards.
    """.trimIndent()
    
    private fun getVehicleIncidentTemplate(context: String): String = """
        You are a safety expert analyzing a vehicle/mobile equipment incident. Generate a professional Root Cause Analysis focusing on traffic safety and equipment operation.

        $context

        Generate EXACTLY this format:

        FIVE WHYS:
        1. Why did this vehicle incident occur? [Immediate cause - collision, tip-over, struck-by]
        2. Why wasn't the incident prevented by operator actions? [Visibility, speed, procedure, skill factors]
        3. Why weren't traffic control measures effective? [Signage, barriers, procedures, communication]
        4. Why isn't the vehicle safety program more effective? [Training, maintenance, supervision, enforcement]
        5. Why isn't there better organizational focus on vehicle safety? [Safety culture, policy, accountability]

        CORRECTIVE ACTIONS (Immediate - next 24-48 hours):
        - [Immediate medical attention for injuries]
        - [Vehicle/equipment inspection and removal from service]
        - [Traffic pattern modification if needed]
        - [Similar operation safety assessment]

        PREVENTIVE ACTIONS (Long-term - next 30-90 days):
        - [Operator training and certification enhancement]
        - [Vehicle maintenance program improvement]
        - [Traffic control and visibility improvement]
        - [Speed control and monitoring system]
        - [Pedestrian/vehicle separation enhancement]

        Address forklifts, cranes, trucks, and other mobile equipment in construction and warehouse environments. Include both operator and pedestrian safety.
    """.trimIndent()
    
    private fun getFireExplosionTemplate(context: String): String = """
        You are a safety expert analyzing a fire or explosion incident. Generate a professional Root Cause Analysis focusing on fire prevention and emergency response.

        $context

        Generate EXACTLY this format:

        FIVE WHYS:
        1. Why did this fire/explosion occur? [Immediate ignition source and fuel combination]
        2. Why were fire prevention controls inadequate? [Hot work, storage, handling, system failures]
        3. Why weren't fire safety measures more effective? [Detection, suppression, training, procedure gaps]
        4. Why isn't the fire safety program more comprehensive? [Emergency planning, equipment maintenance, training systems]
        5. Why isn't there stronger organizational commitment to fire safety? [Safety culture, resource allocation, regulatory compliance]

        CORRECTIVE ACTIONS (Immediate - next 24-48 hours):
        - [Emergency response and damage assessment]
        - [Fire/explosion area isolation and investigation]
        - [Hot work permit suspension if applicable]
        - [Similar fire hazard inspection throughout facility]

        PREVENTIVE ACTIONS (Long-term - next 30-90 days):
        - [Fire prevention training enhancement]
        - [Hot work procedure improvement]
        - [Fire detection/suppression system upgrade]
        - [Combustible material storage control]
        - [Emergency evacuation procedure enhancement]

        Address construction hot work (welding, cutting, grinding) and warehouse fire hazards (storage, electrical, heating systems). Include emergency response and evacuation considerations.
    """.trimIndent()
    
    private fun getConfinedSpaceTemplate(context: String): String = """
        You are a safety expert analyzing a confined space incident. Generate a professional Root Cause Analysis focusing on atmospheric hazards and entry procedures.

        $context

        Generate EXACTLY this format:

        FIVE WHYS:
        1. Why did this confined space incident occur? [Immediate atmospheric or physical hazard]
        2. Why were confined space entry controls inadequate? [Permit, testing, ventilation, communication failures]
        3. Why weren't confined space safety procedures followed? [Training, supervision, equipment, procedure gaps]
        4. Why isn't the confined space program more effective? [Management oversight, resource allocation, compliance]
        5. Why isn't there stronger organizational commitment to confined space safety? [Safety culture, regulatory compliance, emergency preparedness]

        CORRECTIVE ACTIONS (Immediate - next 24-48 hours):
        - [Emergency rescue and medical response]
        - [Confined space isolation and atmospheric testing]
        - [Entry permit system suspension and review]
        - [Similar confined space hazard assessment]

        PREVENTIVE ACTIONS (Long-term - next 30-90 days):
        - [Confined space training and certification enhancement]
        - [Atmospheric monitoring equipment upgrade]
        - [Ventilation and purging procedure improvement]
        - [Emergency rescue procedure enhancement]
        - [Confined space identification and marking system]

        Address tanks, vessels, pits, trenches, and other confined spaces in construction and warehouse environments. Include atmospheric testing, ventilation, and emergency rescue considerations.
    """.trimIndent()
    
    private fun getGeneralSafetyTemplate(context: String): String = """
        You are a safety expert analyzing a workplace safety incident. Generate a professional Root Cause Analysis using systematic investigation principles.

        $context

        Generate EXACTLY this format:

        FIVE WHYS:
        1. Why did this incident occur? [Immediate cause - unsafe act, unsafe condition, or both]
        2. Why was this immediate cause present? [Contributing factors - human factors, environmental conditions]
        3. Why weren't these contributing factors prevented? [System gaps - training, procedures, equipment, supervision]
        4. Why aren't safety management systems more effective? [Management oversight, resource allocation, accountability]
        5. Why isn't there stronger organizational commitment to safety? [Safety culture, leadership, continuous improvement]

        CORRECTIVE ACTIONS (Immediate - next 24-48 hours):
        - [Immediate hazard control or elimination]
        - [Medical attention or injury care if needed]
        - [Similar hazard identification and control]
        - [Incident documentation and reporting]

        PREVENTIVE ACTIONS (Long-term - next 30-90 days):
        - [Training or education improvement]
        - [Procedure or policy enhancement]
        - [Equipment or engineering control implementation]
        - [Supervision or accountability improvement]
        - [Safety management system enhancement]

        Focus on fundamental safety management principles applicable to construction and warehouse environments. Address both immediate hazard control and systemic safety improvements.
    """.trimIndent()
}
