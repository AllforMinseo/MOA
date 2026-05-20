package com.example.a20260310.ui.common

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * [ScrollView]/[androidx.core.widget.NestedScrollView] 안에 둔 RecyclerView가
 * `wrap_content` 높이로 잘리지 않고 전체 아이템 높이를 갖도록 한다.
 * RecyclerView에는 [RecyclerView.setNestedScrollingEnabled](false) 권장.
 */
class WrappingLinearLayoutManager(context: Context) : LinearLayoutManager(context, VERTICAL, false) {

    override fun onMeasure(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        widthSpec: Int,
        heightSpec: Int,
    ) {
        super.onMeasure(
            recycler,
            state,
            widthSpec,
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
    }
}
