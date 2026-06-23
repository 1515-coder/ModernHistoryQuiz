# Project-specific ProGuard rules.
# JSON models are accessed directly by Java code, so no reflection keep rules are required.
# SwipeDrawerLayout uses reflection to widen DrawerLayout's edge.
-keepclassmembers class androidx.drawerlayout.widget.DrawerLayout {
    androidx.customview.widget.ViewDragHelper mLeftDragger;
}

-keepclassmembers class androidx.customview.widget.ViewDragHelper {
    int mEdgeSize;
}