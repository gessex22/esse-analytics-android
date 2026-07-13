package com.esseanalytics.android.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Misma escala que --radius del frontend web (theme.css:33, 0.625rem = 10px)
// y su derivada --radius-sm/-lg (theme.css:108-111) — portada a dp 1:1.
val EsseAnalyticsShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
)
