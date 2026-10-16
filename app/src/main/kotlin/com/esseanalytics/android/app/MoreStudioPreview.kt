package com.esseanalytics.android.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.esseanalytics.android.core.designsystem.theme.CreatorHomeTheme

@Preview(name = "Más · claro", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun MoreLightPreview() {
    CreatorHomeTheme { MoreStudioContent(moreStudioPreviewItems(isOwner = false, canUseCloudStorage = false)) }
}

@Preview(name = "Más · oscuro", showBackground = true, widthDp = 390, heightDp = 844, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MoreDarkPreview() {
    CreatorHomeTheme(darkTheme = true) { MoreStudioContent(moreStudioPreviewItems(isOwner = true, canUseCloudStorage = true)) }
}

@Preview(name = "Más · 320 dp y texto grande", showBackground = true, widthDp = 320, heightDp = 800, fontScale = 1.5f)
@Composable
private fun MoreLargeTextPreview() {
    CreatorHomeTheme { MoreStudioContent(moreStudioPreviewItems(isOwner = false, canUseCloudStorage = true)) }
}

@Preview(name = "Más · propietario y nube", showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun MoreOwnerCloudPreview() {
    CreatorHomeTheme { MoreStudioContent(moreStudioPreviewItems(isOwner = true, canUseCloudStorage = true)) }
}
