package ghart.space.pi_drive.di

import javax.inject.Qualifier

/**
 * Hilt qualifier for the application-lifetime [kotlinx.coroutines.CoroutineScope].
 *
 * Scope bindings annotated with [ApplicationScope] live for the full lifetime of
 * the [PiDriveApplication] process. Use this scope for [Singleton]-scoped objects
 * (like [DemoVehicleDataSource]) that need to launch long-running coroutines.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationScope
