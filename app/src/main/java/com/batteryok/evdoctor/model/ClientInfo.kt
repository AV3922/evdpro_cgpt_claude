package com.batteryok.evdoctor.model

import java.io.Serializable

data class ClientInfo(
    val name: String = "",
    val clientId: String = "",
    val phone: String = "",
    val email: String = ""
) : Serializable
