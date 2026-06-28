package com.trevio.android.di

import com.trevio.android.data.remote.FirebaseAuthServiceImpl
import com.trevio.android.data.remote.FirebaseExpenseServiceImpl
import com.trevio.android.data.remote.FirebaseGroupServiceImpl
import com.trevio.android.data.remote.FirebaseNotificationServiceImpl
import com.trevio.android.data.remote.FirebaseSettlementServiceImpl
import com.trevio.android.data.remote.FirebaseUserServiceImpl
import com.trevio.android.domain.repository.AuthService
import com.trevio.android.domain.repository.ExpenseService
import com.trevio.android.domain.repository.GroupService
import com.trevio.android.domain.repository.NotificationService
import com.trevio.android.domain.repository.SettlementService
import com.trevio.android.domain.repository.UserService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    @Singleton
    abstract fun bindAuthService(impl: FirebaseAuthServiceImpl): AuthService

    @Binds
    @Singleton
    abstract fun bindUserService(impl: FirebaseUserServiceImpl): UserService

    @Binds
    @Singleton
    abstract fun bindGroupService(impl: FirebaseGroupServiceImpl): GroupService

    @Binds
    @Singleton
    abstract fun bindExpenseService(impl: FirebaseExpenseServiceImpl): ExpenseService

    @Binds
    @Singleton
    abstract fun bindSettlementService(impl: FirebaseSettlementServiceImpl): SettlementService

    @Binds
    @Singleton
    abstract fun bindNotificationService(impl: FirebaseNotificationServiceImpl): NotificationService
}
