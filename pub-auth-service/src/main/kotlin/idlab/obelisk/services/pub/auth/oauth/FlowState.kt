package idlab.obelisk.services.pub.auth.oauth

data class FlowState(val caseNumber: CaseNumber, var sessionId: String?=null, var code: String? =null, var idToken: String? = null)