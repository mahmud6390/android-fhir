package com.google.android.fhir.demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.rest.gclient.DateClientParam
import ca.uhn.fhir.rest.gclient.StringClientParam
import ca.uhn.fhir.rest.gclient.TokenClientParam
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.search.Order
import com.google.android.fhir.search.Search
import com.google.android.fhir.search.StringFilterModifier
import com.google.android.fhir.search.count
import com.google.android.fhir.search.search
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Group
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Person.SP_NAME
import timber.log.Timber

/**
 * The ViewModel helper class for FamilyItemRecyclerViewAdapter, that is responsible for preparing
 * data for UI.
 */
class FamilyListViewModel(application: Application, private val fhirEngine: FhirEngine) :
  AndroidViewModel(application) {

  val liveSearchedFamilies = MutableLiveData<List<FamilyItem>>()
  val familyCount = MutableLiveData<Long>()

  init {
    updateFamilyListAndFamilyCount({ getSearchResults() }, { count() })
  }

  fun searchFamiliesByName(nameQuery: String) {
    updateFamilyListAndFamilyCount({ getSearchResults(nameQuery) }, { count(nameQuery) })
  }

  /**
   * [updateFamilyListAndFamilyCount] calls the search and count lambda and updates the live data
   * values accordingly. It is initially called when this [ViewModel] is created. Later its called
   * by the client every time search query changes or data-sync is completed.
   */
  private fun updateFamilyListAndFamilyCount(
    search: suspend () -> List<FamilyItem>,
    count: suspend () -> Long
  ) {
    viewModelScope.launch {
      liveSearchedFamilies.value = search()
      familyCount.value = count()
    }
  }

  /**
   * Returns count of all the [Family] who match the filter criteria unlike [getSearchResults]
   * which only returns a fixed range.
   */
  private suspend fun count(nameQuery: String = ""): Long {
    return fhirEngine.count<Group> {
      if (nameQuery.isNotEmpty()) {
        filter(
          StringClientParam(SP_NAME),
          {
            modifier = StringFilterModifier.CONTAINS
            value = nameQuery
          }
        )
      }
      filterFamily(this)
    }
  }

  private suspend fun getSearchResults(nameQuery: String = ""): List<FamilyItem> {
    val families: MutableList<FamilyItem> = mutableListOf()

    Timber.d("LoadTest: Search Start")
    fhirEngine
      .search<Group> {
        if (nameQuery.isNotEmpty()) {
          filter(
            StringClientParam(SP_NAME),
            {
              modifier = StringFilterModifier.CONTAINS
              value = nameQuery
            }
          )
        }
        filterFamily(this)
        sort(DateClientParam("_lastUpdated"), Order.DESCENDING)
        //count = 100
        from = 0
      }
      .mapIndexed { index, fhirGroup -> fhirGroup.toFamilyItem(index + 1) }
      .let { families.addAll(it) }

    Timber.d("LoadTest: Search End")
    return families
  }

  private fun filterFamily(search: Search) {
    search.filter(
      TokenClientParam("type"),
      init = arrayOf(
        {
          val coding = Coding("http://hl7.org/fhir/group-type", "person", "")
          value = of(coding)
        },
      ),
    )

    search.filter(
      TokenClientParam("code"),
      init = arrayOf(
        {
          val coding = Coding("https://www.snomed.org", "35359004", "")
          value = of(coding)
        },
      ),
    )
  }

  /** The Family's details for display purposes. */
  data class FamilyItem(
    val id: String,
    val resourceId: String,
    val name: String,
    val isActive: Boolean,
    val html: String,
    var risk: String? = "",
    var riskItem: RiskAssessmentItem? = null
  ) {
    override fun toString(): String = name
  }

  /** The Observation's details for display purposes. */
  data class ObservationItem(
    val id: String,
    val code: String,
    val effective: String,
    val value: String
  ) {
    override fun toString(): String = code
  }

  data class ConditionItem(
    val id: String,
    val code: String,
    val effective: String,
    val value: String
  ) {
    override fun toString(): String = code
  }

  class FamilyListViewModelFactory(
      private val application: Application,
      private val fhirEngine: FhirEngine
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      if (modelClass.isAssignableFrom(FamilyListViewModel::class.java)) {
        return FamilyListViewModel(application, fhirEngine) as T
      }
      throw IllegalArgumentException("Unknown ViewModel class")
    }
  }
}

internal fun Group.toFamilyItem(position: Int): FamilyListViewModel.FamilyItem {
  // Show nothing if no values available for gender and date of birth.
  val patientId = if (hasIdElement()) idElement.idPart else ""
  val name = if (hasName()) name else ""
  val isActive = active
  val html: String = if (hasText()) text.div.valueAsString else ""

  return FamilyListViewModel.FamilyItem(
    id = position.toString(),
    resourceId = patientId,
    name = name,
    isActive = isActive,
    html = html
  )
}