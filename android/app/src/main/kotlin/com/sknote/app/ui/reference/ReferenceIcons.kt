package com.sknote.app.ui.reference

import com.sknote.app.data.model.ReferenceItem
import com.sknote.app.util.ReferenceIconResolver
import com.sknote.app.util.ReferenceTypeLabels

object ReferenceIcons {

    fun getTypeLabel(type: String): String = ReferenceTypeLabels.get(type)

    fun getIconRes(item: ReferenceItem): Int {
        return ReferenceIconResolver.resolve(item.name, item.type)
    }
}
