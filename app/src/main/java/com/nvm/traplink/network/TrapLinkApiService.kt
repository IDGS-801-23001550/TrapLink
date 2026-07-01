package com.nvm.traplink.network // Asegúrate de que tenga el .network si lo metiste en la carpeta

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface TrapLinkApiService {
    @POST("api/Usuarios/login")
    suspend fun login(@Body request: LoginRequestDto): Response<LoginResponseDto>
}