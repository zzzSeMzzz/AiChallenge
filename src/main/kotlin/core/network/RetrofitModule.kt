package core.network

import core.BuildConfig
import core.SERVER_URL
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


object RetrofitModule { // Изменён на object для синглтона


    private val authInterceptor = okhttp3.Interceptor { chain ->
        val newRequest = chain.request().newBuilder()
            .addHeader("Authorization", "Api-Key ${BuildConfig.YA_API_KEY}")
            .build()
        chain.proceed(newRequest)
    }

    /*private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }*/

    private val okHttpClient: OkHttpClient by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(35, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(1, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        client.addInterceptor(loggingInterceptor)
        client.build()
    }

    val retrofit: Retrofit by lazy {
        provideRetrofit(okHttpClient)
    }

    private fun provideRetrofit(
        okHttpClient: OkHttpClient,
    ): Retrofit {
        return Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .baseUrl(SERVER_URL)
            .client(okHttpClient)
            .build()
    }
}