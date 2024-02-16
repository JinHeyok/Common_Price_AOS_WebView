package com.banmal.web

import retrofit2.Call
import retrofit2.http.Body

import retrofit2.http.POST

interface RetrofitApi {

    @POST("/api/v1/auth/token/update")
    fun token(@Body post: FcmToken?): Call<Message>?

}