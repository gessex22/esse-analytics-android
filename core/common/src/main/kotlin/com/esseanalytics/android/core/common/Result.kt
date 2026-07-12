package com.esseanalytics.android.core.common

// Wrapper propio (no kotlin.Result) para poder distinguir "cargando" de
// "listo" en los StateFlow de los ViewModel sin anidar Result<Result<T>>.
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : AppResult<Nothing>
    data object Loading : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Error -> this
    is AppResult.Loading -> this
}
