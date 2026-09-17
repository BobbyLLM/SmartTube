package com.liskovsoft.youtubeapi.app.potokennp2.visitor

import com.liskovsoft.youtubeapi.app.potokennp2.visitor.data.VisitorResult
import com.liskovsoft.googlecommon.common.converters.gson.WithGson
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

@WithGson
internal interface VisitorApi {
    @Headers(
        "Content-Type: application/json",
        "Accept-Language: en-US, en;q=0.9",
        "Cookie: SOCS=CAE=",
        "Host: www.youtube.com",
        "Origin: https://www.youtube.com",
        "Referer: https://www.youtube.com",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0",
        "X-Youtube-Client-Name: 1",
        "X-Youtube-Client-Version: 2.20250213.05.00"
        )
    @POST("https://www.youtube.com/youtubei/v1/visitor_id")
    fun getVisitorId(@Body query: String = VisitorApiHelper.getVisitorQuery()): Call<VisitorResult?>
}