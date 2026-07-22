package com.charles.crowdtransit.app.di

import com.charles.crowdtransit.app.BuildConfig
import com.charles.crowdtransit.app.data.feedback.GithubApi
import com.charles.crowdtransit.app.data.remote.TransitlandApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Provides
    @Singleton
    fun provideTransitlandApiKeyInterceptor(): Interceptor = Interceptor { chain ->
        val original = chain.request()
        val urlWithKey = original.url.newBuilder()
            .addQueryParameter("apikey", BuildConfig.TRANSITLAND_API_KEY)
            .build()
        chain.proceed(original.newBuilder().url(urlWithKey).build())
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(apiKeyInterceptor: Interceptor): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 32
        }
        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(5, TimeUnit.SECONDS)
            // Departures for busy hub stations (large stop_pattern/route payloads) can take
            // longer than 5s to build server-side; 5s caused real timeouts on stations like
            // Bristol Temple Meads. Matches the GitHub client's read timeout below.
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BASIC
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                },
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideTransitlandApi(client: OkHttpClient, moshi: Moshi): TransitlandApi =
        Retrofit.Builder()
            .baseUrl("https://api.transit.land/".toHttpUrl())
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TransitlandApi::class.java)

    // Public Worker endpoint (workers/ors-proxy) that holds the OpenRouteService key as a
    // Cloudflare secret — the key itself must never appear in this app (the APK is public).
    private const val ORS_PROXY_BASE_URL = "https://crowdtransit-ors-proxy.chartmann1590.workers.dev/"

    @Provides
    @Singleton
    fun provideOrsApi(moshi: Moshi): com.charles.crowdtransit.app.data.remote.OrsApi =
        Retrofit.Builder()
            .baseUrl(ORS_PROXY_BASE_URL.toHttpUrl())
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build(),
            )
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(com.charles.crowdtransit.app.data.remote.OrsApi::class.java)

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class GithubOkHttp

    @Provides
    @Singleton
    @GithubOkHttp
    fun provideGithubOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "CrowdTransit-Android/1.0.0")
                val token = BuildConfig.GITHUB_API_TOKEN
                if (token.isNotEmpty()) {
                    builder.header("Authorization", "Bearer $token")
                }
                chain.proceed(builder.build())
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BASIC
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                },
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideGithubApi(
        @GithubOkHttp client: OkHttpClient,
        moshi: Moshi,
    ): GithubApi =
        Retrofit.Builder()
            .baseUrl("https://api.github.com/".toHttpUrl())
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GithubApi::class.java)
}
