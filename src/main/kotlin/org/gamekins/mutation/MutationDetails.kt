package org.gamekins.mutation

data class MutationDetails(
    val methodInfo: Map<String, String>,
    val instructionIndices: List<Int>,
    val mutationOperatorName: String,
    val fileName: String,
    val loc: Int,
    val mutationDescription: String
)