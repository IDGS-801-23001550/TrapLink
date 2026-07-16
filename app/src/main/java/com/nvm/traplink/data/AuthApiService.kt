package com.nvm.traplink.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequestDto
    ): Response<LoginResponseDto>

    @POST("auth/registrar")
    suspend fun registrar(
        @Body request: RegistroRequestDto
    ): Response<RegistroResponseDto>
}