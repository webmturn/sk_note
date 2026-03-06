package com.sknote.app.ui.reference

import com.sknote.app.R
import com.sknote.app.data.model.ReferenceItem

object ReferenceIcons {

    fun getTypeLabel(type: String): String = when (type) {
        "block" -> "积木块"
        "component" -> "组件"
        "widget" -> "控件"
        "event" -> "事件"
        else -> type
    }

    fun getIconRes(item: ReferenceItem): Int {
        val name = (item.name ?: "").lowercase()
        return when (item.type) {
            "component" -> when {
                name.contains("intent") -> R.drawable.ic_comp_intent
                name.contains("sharedpreferences") -> R.drawable.ic_comp_sharedpref
                name.contains("sqlite") -> R.drawable.ic_comp_database
                name.contains("calendar") -> R.drawable.ic_comp_calendar
                name.contains("vibrator") -> R.drawable.ic_comp_vibrate
                name.contains("timer") -> R.drawable.ic_comp_timer
                name.contains("progressdialog") -> R.drawable.ic_comp_dialog
                name.contains("timepickerdialog") -> R.drawable.ic_comp_timer
                name.contains("dialog") -> R.drawable.ic_comp_dialog
                name.contains("mediaplayer") -> R.drawable.ic_comp_media
                name.contains("soundpool") -> R.drawable.ic_comp_sound
                name.contains("camera") -> R.drawable.ic_comp_camera
                name.contains("filepicker") -> R.drawable.ic_comp_file
                name.contains("requestnetwork") -> R.drawable.ic_comp_network
                name.contains("firebase") -> R.drawable.ic_comp_firebase
                name.contains("interstitial") || name.contains("rewarded") -> R.drawable.ic_comp_ad
                name.contains("objectanimator") -> R.drawable.ic_comp_animation
                name.contains("gyroscope") -> R.drawable.ic_comp_gyroscope
                name.contains("texttospeech") -> R.drawable.ic_comp_tts
                name.contains("speechtotext") -> R.drawable.ic_comp_stt
                name.contains("bluetooth") -> R.drawable.ic_comp_bluetooth
                name.contains("location") -> R.drawable.ic_comp_location
                name.contains("notification") -> R.drawable.ic_comp_notification
                else -> R.drawable.ic_comp_default
            }
            "widget" -> when {
                name.contains("button") && name.contains("image") -> R.drawable.ic_widget_imagebutton
                name.contains("button") && name.contains("float") -> R.drawable.ic_widget_fab
                name.contains("button") && name.contains("radio") -> R.drawable.ic_widget_radio
                name.contains("button") -> R.drawable.ic_widget_button
                name.contains("textview") -> R.drawable.ic_widget_text
                name.contains("edittext") || name.contains("autocomplete") -> R.drawable.ic_widget_edittext
                name.contains("imageview") || name.contains("circleimage") -> R.drawable.ic_widget_image
                name.contains("listview") || name.contains("recyclerview") -> R.drawable.ic_widget_list
                name.contains("gridview") -> R.drawable.ic_widget_grid
                name.contains("spinner") -> R.drawable.ic_widget_spinner
                name.contains("checkbox") -> R.drawable.ic_widget_checkbox
                name.contains("switch") -> R.drawable.ic_widget_switch
                name.contains("seekbar") -> R.drawable.ic_widget_seekbar
                name.contains("ratingbar") -> R.drawable.ic_widget_rating
                name.contains("webview") -> R.drawable.ic_widget_web
                name.contains("progressbar") -> R.drawable.ic_widget_progress
                name.contains("mapview") -> R.drawable.ic_widget_map
                name.contains("calendarview") || name.contains("datepicker") -> R.drawable.ic_widget_calendar
                name.contains("adview") -> R.drawable.ic_widget_ad
                name.contains("videoview") -> R.drawable.ic_widget_video
                name.contains("searchview") -> R.drawable.ic_widget_search
                name.contains("timepicker") -> R.drawable.ic_widget_timer
                name.contains("viewpager") -> R.drawable.ic_widget_viewpager
                name.contains("cardview") -> R.drawable.ic_widget_card
                name.contains("tablayout") -> R.drawable.ic_widget_tab
                name.contains("bottomnavigation") -> R.drawable.ic_widget_bottomnav
                name.contains("drawer") || name.contains("navigation") -> R.drawable.ic_widget_drawer
                name.contains("textinput") -> R.drawable.ic_widget_edittext
                name.contains("scroll") || name.contains("nested") -> R.drawable.ic_widget_scroll
                name.contains("swiperefresh") -> R.drawable.ic_widget_refresh
                name.contains("coordinator") || name.contains("collapsing") -> R.drawable.ic_widget_layout
                name.contains("linear") || name.contains("relative") || name.contains("frame") -> R.drawable.ic_widget_layout
                name.contains("fab") -> R.drawable.ic_widget_fab
                else -> R.drawable.ic_widget_default
            }
            "event" -> when {
                name.contains("longclick") -> R.drawable.ic_event_longclick
                name.contains("doubleclick") -> R.drawable.ic_event_click
                name.contains("click") -> R.drawable.ic_event_click
                name.contains("text") || name.contains("editor") -> R.drawable.ic_event_text
                name.contains("item") || name.contains("selected") -> R.drawable.ic_event_select
                name.contains("checked") -> R.drawable.ic_event_check
                name.contains("seekbar") || name.contains("rating") -> R.drawable.ic_event_seekbar
                name.contains("create") || name.contains("resume") || name.contains("pause") ||
                    name.contains("destroy") || name.contains("start") || name.contains("stop") ||
                    name.contains("backpress") -> R.drawable.ic_event_lifecycle
                name.contains("page") -> R.drawable.ic_event_page
                name.contains("scroll") -> R.drawable.ic_event_scroll
                name.contains("menu") || name.contains("option") -> R.drawable.ic_event_menu
                name.contains("result") || name.contains("permission") -> R.drawable.ic_event_result
                name.contains("firebase") -> R.drawable.ic_event_firebase
                name.contains("network") || name.contains("response") || name.contains("error") -> R.drawable.ic_event_network
                name.contains("refresh") -> R.drawable.ic_event_refresh
                name.contains("tab") -> R.drawable.ic_event_tab
                else -> R.drawable.ic_event_default
            }
            else -> R.drawable.ic_comp_default
        }
    }
}
