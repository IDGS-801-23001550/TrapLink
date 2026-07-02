package com.nvm.traplink.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("usuarios/login")
    suspend fun login(
        @Body request: LoginRequestDto
    ): Response<LoginResponseDto>

    @POST("usuarios/registrar")
    suspend fun registrar(
        @Body request: RegistroRequestDto
    ): Response<RegistroResponseDto>
}