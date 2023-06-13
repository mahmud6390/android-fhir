/*
 * Copyright 2021 Google LLC
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

import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.fhir.demo.databinding.FamilyListItemViewBinding

class FamilyItemViewHolder(binding: FamilyListItemViewBinding) :
  RecyclerView.ViewHolder(binding.root) {
  private val statusView: ImageView = binding.status
  private val nameView: TextView = binding.name
  private val idView: TextView = binding.id

  fun bindTo(
    familyItem: FamilyListViewModel.FamilyItem,
    onItemClicked: (FamilyListViewModel.FamilyItem) -> Unit
  ) {
    this.nameView.text = familyItem.name
    this.idView.text = "Id: #---${familyItem.resourceId}"
  }
}
