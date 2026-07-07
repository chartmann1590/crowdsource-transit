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
            .readTimeout(5, TimeUnit.SECONDS)
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
