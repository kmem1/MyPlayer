package com.kmem.myplayer.di

import com.kmem.myplayer.core_data.repositories.MusicRepository
import com.kmem.myplayer.feature_player.domain.repository.PlayerControllerRepository
import com.kmem.myplayer.feature_player.domain.repository.PlayerServiceRepository
import com.kmem.myplayer.feature_playlist.domain.repository.FileChooserRepository
import com.kmem.myplayer.feature_playlist.domain.repository.PlaylistRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class AppModule {

    @Provides
    @Singleton
    fun providePlaylistRepository(): PlaylistRepository {
        return MusicRepository.getInstance()
    }

    @Provides
    @Singleton
    fun provideFileChooserRepository(): FileChooserRepository {
        return MusicRepository.getInstance()
    }

    @Provides
    @Singleton
    fun providePlayerControllerRepository(): PlayerControllerRepository {
        return MusicRepository.getInstance()
    }

    @Provides
    @Singleton
    fun providePlayerServiceRepository(): PlayerServiceRepository {
        return MusicRepository.getInstance()
    }
}