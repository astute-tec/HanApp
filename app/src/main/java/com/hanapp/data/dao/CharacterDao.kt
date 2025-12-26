package com.hanapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hanapp.data.model.Character
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters WHERE id = :charId")
    suspend fun getCharacterById(charId: String): Character?

    @Query("SELECT * FROM characters WHERE grade = :grade")
    fun getCharactersByGradeFlow(grade: Int): Flow<List<Character>>

    @Query("SELECT * FROM characters WHERE grade = :grade")
    suspend fun getAllCharactersByGrade(grade: Int): List<Character>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(characters: List<Character>)

    @Query("SELECT COUNT(*) FROM characters")
    suspend fun getCount(): Int

    @Query("SELECT * FROM characters ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomCharacter(): Character?

    @Query("SELECT * FROM characters WHERE grade = :grade ORDER BY id LIMIT 1")
    suspend fun getFirstCharacterByGrade(grade: Int): Character?

    @Query("SELECT * FROM characters WHERE id > :currentCharId AND grade = :grade ORDER BY id LIMIT 1")
    suspend fun getNextCharacter(currentCharId: String, grade: Int): Character?

    @Query("DELETE FROM characters")
    suspend fun deleteAll()
}
