/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.fhir.demo

import android.app.Application
import android.content.Context
import com.google.android.fhir.DatabaseErrorStrategy.RECREATE_AT_OPEN
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.FhirEngineConfiguration
import com.google.android.fhir.FhirEngineProvider
import com.google.android.fhir.NetworkConfiguration
import com.google.android.fhir.ServerConfiguration
import com.google.android.fhir.datacapture.DataCaptureConfig
import com.google.android.fhir.datacapture.XFhirQueryResolver
import com.google.android.fhir.demo.data.FhirSyncWorker
import com.google.android.fhir.search.search
import com.google.android.fhir.sync.Authenticator
import com.google.android.fhir.sync.Sync
import com.google.android.fhir.sync.remote.HttpLogger
import org.hl7.fhir.r4.model.Patient
import timber.log.Timber

class FhirApplication : Application(), DataCaptureConfig.Provider {
  // Only initiate the FhirEngine when used for the first time, not when the app is created.
  private val fhirEngine: FhirEngine by lazy { constructFhirEngine() }

  private var dataCaptureConfig: DataCaptureConfig? = null

  private val dataStore by lazy { DemoDataStore(this) }

  override fun onCreate() {
    super.onCreate()
    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }
    Patient.IDENTIFIER
    FhirEngineProvider.init(
      FhirEngineConfiguration(
        enableEncryptionIfSupported = true,
        RECREATE_AT_OPEN,
        ServerConfiguration(
          "http://fhirqa.mpower-social.com:7070/fhir/",
          authenticator = object: Authenticator {
            override fun getAccessToken(): String {
              return "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJIUTktRnk2NXM4WTRPb2pvOWFsRk9iSW02dHdISFJTNUpKdGVrZEFUeHFFIn0.eyJleHAiOjE2ODc0NDg2NjYsImlhdCI6MTY4NzQxMjY2NiwianRpIjoiMTQ2MGM5ZTAtODBhMC00MjIxLThhYWUtNTgwMjcwMzdmMGI3IiwiaXNzIjoiaHR0cHM6Ly9rZXljbG9hay1uZXcubXBvd2VyLXNvY2lhbC5jb20vYXV0aC9yZWFsbXMvZmhpciIsImF1ZCI6WyJyZWFsbS1tYW5hZ2VtZW50IiwiYWNjb3VudCJdLCJzdWIiOiJmOGVhODZlMy02YjMyLTQ5MzQtODY2OS1hMzRjN2Y2OTRjNTkiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJmaGlyLWFwcCIsInNlc3Npb25fc3RhdGUiOiJmZGQ4YzlmYS0zZmUyLTQyNDUtOGY5Mi02NDEyYzJmZThmNjEiLCJhY3IiOiIxIiwiYWxsb3dlZC1vcmlnaW5zIjpbImh0dHA6Ly9sb2NhbGhvc3Q6ODA4MCIsImh0dHA6Ly9maGlycWEubXBvd2VyLXNvY2lhbC5jb206NzA3MCIsImh0dHA6Ly8xOTIuMTY4LjE5LjYzOjgwODAiLCJodHRwOi8vMTkyLjE2OC4xOS42Mzo3MDcwIiwiaHR0cDovL2ZoaXItd2ViLm1wb3dlci1zb2NpYWwuY29tOjMwMDAiLCJodHRwOi8vMTkyLjE2OC4yMy4xNDo4MDgwIiwiaHR0cDovL2ZoaXIubXBvd2VyLXNvY2lhbC5jb206NzA3MCIsImh0dHA6Ly8xOTIuMTY4LjQzLjIxNDo4MDgwIiwiaHR0cDovL2xvY2FsaG9zdDozMDAwIiwiaHR0cDovLzE5Mi4xNjguMTkuNjM6MzAwMCIsImh0dHA6Ly9maGlyLXdlYnFhLm1wb3dlci1zb2NpYWwuY29tOjMwMDAiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIkxPQ0FUSU9OUyIsIkVESVRfS0VZQ0xPQUtfVVNFUlMiLCJST0xFX1ZJRVdfS0VZQ0xPQUtfVVNFUlMiLCJIRUFMVEhDQVJFX1NFUlZJQ0UiLCJDT01NT0RJVFkiLCJFREkiLCJHUk9VUCIsIlJPTEVfRURJVF9LRVlDTE9BS19VU0VSUyIsImRlZmF1bHQtcm9sZXMtZmhpciIsIlRFQU1TIiwiVklFV19LRVlDTE9BS19VU0VSUyIsIm9mZmxpbmVfYWNjZXNzIiwidW1hX2F1dGhvcml6YXRpb24iLCJVU0VSUyIsIkNBUkVfVEVBTSIsIlFVRVNUIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsicmVhbG0tbWFuYWdlbWVudCI6eyJyb2xlcyI6WyJtYW5hZ2UtdXNlcnMiLCJ2aWV3LXVzZXJzIiwicXVlcnktZ3JvdXBzIiwicXVlcnktdXNlcnMiXX0sImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoib3BlbmlkIGVtYWlsIHByb2ZpbGUiLCJzaWQiOiJmZGQ4YzlmYS0zZmUyLTQyNDUtOGY5Mi02NDEyYzJmZThmNjEiLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsIm5hbWUiOiJyYWkgaGFuIiwicHJlZmVycmVkX3VzZXJuYW1lIjoicmFpIiwiZ2l2ZW5fbmFtZSI6InJhaSIsImZhbWlseV9uYW1lIjoiaGFuIn0.gWfZnGKTgRjGGW_o_ejDyq_oYdypF2nMFsCZt6NNRvabKl1rndQ0udawihNPvQL82uHcod-EywAeQAd-GU_XZzdL2eTN_Xq53TOynkbUmdqTo209rJ2QihZj1BMR1kfbnayZxOuuX5PTngKbE2vKEXeFK60gRe2Syhrh9PBQFbob8hANMbPltXzszye5t5ghlQnfh1M7__uCaneggEZ3CFpAaGsGQgFO4pIE7YSUQrq8iU57T-ZQFCtrED4VRStDPLf6uAkfL5h4SFLvrh-l-ElQmox-BFD_ppIxuauuuT-YxKLS7n7kEcy8B4Cf-VFT8AD-Zlo0Pm3iK7ZBqhrGZQ"
            }
          },
          httpLogger =
            HttpLogger(
              HttpLogger.Configuration(
                if (BuildConfig.DEBUG) HttpLogger.Level.BODY else HttpLogger.Level.BASIC
              )
            ) { Timber.tag("App-HttpLog").d(it) },
          networkConfiguration = NetworkConfiguration(uploadWithGzip = false)
        )
      )
    )
    Sync.oneTimeSync<FhirSyncWorker>(this)

    dataCaptureConfig =
      DataCaptureConfig().apply {
        urlResolver = ReferenceUrlResolver(this@FhirApplication as Context)
        xFhirQueryResolver = XFhirQueryResolver { fhirEngine.search(it) }
      }
  }

  private fun constructFhirEngine(): FhirEngine {
    return FhirEngineProvider.getInstance(this)
  }

  companion object {
    fun fhirEngine(context: Context) = (context.applicationContext as FhirApplication).fhirEngine

    fun dataStore(context: Context) = (context.applicationContext as FhirApplication).dataStore
  }

  override fun getDataCaptureConfig(): DataCaptureConfig = dataCaptureConfig ?: DataCaptureConfig()
}
