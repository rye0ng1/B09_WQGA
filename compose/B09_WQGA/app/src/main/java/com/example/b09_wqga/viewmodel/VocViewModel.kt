package com.example.b09_wqga.viewmodel

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.b09_wqga.database.Quiz
import com.example.b09_wqga.database.Voc
import com.example.b09_wqga.database.Word
import com.example.b09_wqga.repository.VocRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Collections.shuffle
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class VocViewModel(private val vocRepository: VocRepository) : ViewModel() {

    private val _vocList = MutableStateFlow<List<Voc>>(emptyList())
    val vocList: StateFlow<List<Voc>> = _vocList

    private val _wordList = MutableStateFlow<List<Word>>(emptyList())
    val wordList: StateFlow<List<Word>> = _wordList

    val searchText = mutableStateOf("")
    val sortBasedOn = mutableStateOf("Headword")
    val sortOrder = mutableStateOf("Ascending")
    val sortOptions = listOf("Headword", "Meaning", "Right", "Wrong")

    val currentQuiz = MutableStateFlow<Word?>(null)

    private fun getCurrentDateTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    fun loadVocs(userId: Int) {
        viewModelScope.launch {
            try {
                val vocs = vocRepository.getAllVocsByUserId(userId)
                _vocList.value = vocs
            } catch (e: Exception) {
                Log.e("VocViewModel", "Error loading vocs", e)
            }
        }
    }

    fun loadWordsByVocId(vocId: Int) {
        Log.i("vocid", "vocid: $vocId")
        viewModelScope.launch {
            val words = vocRepository.getWordsByVocId(vocId)
            _wordList.value = words
            //Log.i("vocid", "word: ${words[0].headword}")
        }
    }

    fun getWordById(wordId: Int): Word? {
        return _wordList.value.find { it.word_id == wordId }
    }

    fun addVoc(voc: Voc, onComplete: (Boolean) -> Unit) {
        getNextAvailableVocId { availableVocId ->
            viewModelScope.launch {
                val vocWithId = voc.copy(voc_id = availableVocId, create_date = getCurrentDateTime())
                val result = vocRepository.addVoc(vocWithId)
                onComplete(result)
                if (result) {
                    loadVocs(voc.user_id)
                }
            }
        }
    }

    fun getVocById(vocId: Int): Voc? {
        return _vocList.value.find { it.voc_id == vocId }
    }

    fun updateVoc(voc: Voc, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = vocRepository.updateVoc(voc.copy(create_date = getCurrentDateTime()))
            onComplete(result)
            if (result) {
                loadVocs(voc.user_id)
            }
        }
    }

    fun deleteVoc(vocId: Int, userId: Int, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = vocRepository.deleteVoc(vocId)
            onComplete(result)
            if (result) {
                loadVocs(userId)
            }
        }
    }

    fun getNextAvailableVocId(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val result = vocRepository.getNextAvailableVocId()
            onComplete(result)
        }
    }

    fun addWord(vocId: Int, headword: String, meanings: List<String>) {
        viewModelScope.launch {
            val newWord = Word(
                word_id = generateUniqueWordId(vocId),
                voc_id = vocId,
                headword = headword,
                meanings = meanings,
                create_date = getCurrentDateTime()
            )
            val success = vocRepository.addWord(vocId, newWord)
            if (success) {
                updateWordCount(vocId, 1) // word_count 증가
            }
            loadWordsByVocId(vocId)
        }
    }

    fun deleteWord(vocId: Int, wordId: Int) {
        viewModelScope.launch {
            val success = vocRepository.deleteWord(vocId, wordId)
            if (success) {
                updateWordCount(vocId, -1) // word_count 감소
            }
            loadWordsByVocId(vocId)
        }
    }

    private suspend fun updateWordCount(vocId: Int, delta: Int) {
        val voc = getVocById(vocId)
        if (voc != null) {
            val updatedVoc = voc.copy(word_count = voc.word_count + delta, create_date = getCurrentDateTime())
            vocRepository.updateVoc(updatedVoc)
        }
    }

    fun updateWord(word: Word) {
        viewModelScope.launch {
            val success = vocRepository.updateWord(word.voc_id, word.copy(create_date = getCurrentDateTime()))
            if (success) {
                loadWordsByVocId(word.voc_id)
            }
        }
    }

    private fun generateUniqueVocId(): Int {
        return (_vocList.value.maxOfOrNull { it.voc_id } ?: 0) + 1
    }

    private fun generateUniqueWordId(vocId: Int): Int {
        val wordsInVoc = _wordList.value.filter { it.voc_id == vocId }
        return (wordsInVoc.maxOfOrNull { it.word_id } ?: 0) + 1
    }

    fun checkWordFull(): Boolean {
        return _wordList.value.size >= 60
    }

    fun isVocListFull(): Boolean {
        return _vocList.value.size >= 10
    }

    fun sortVocList() {
        val sortedList = _vocList.value.sortedWith(compareBy<Voc> {
            !it.title.contains(searchText.value, ignoreCase = true)
        }.thenBy {
            it.title
        })
        _vocList.value = sortedList
    }

    fun sortWordList() {
        val sortedList = when (sortBasedOn.value) {
            "Headword" -> if (sortOrder.value == "Ascending") {
                _wordList.value.sortedWith(compareBy<Word> {
                    !it.headword.contains(searchText.value, ignoreCase = true)
                }.thenBy {
                    it.headword
                })
                //_wordList.value.sortedBy { it.headword }
            } else {
                _wordList.value.sortedWith(compareBy<Word> {
                    it.headword.contains(searchText.value, ignoreCase = true)
                }.thenBy {
                    it.headword
                })
                //_wordList.value.sortedByDescending { it.headword }
            }
            "Meaning" -> if (sortOrder.value == "Ascending") {
                _wordList.value.sortedWith(compareBy<Word> {
                    !it.headword.contains(searchText.value, ignoreCase = true)
                }.thenBy {
                    it.meanings.joinToString()
                })
                //_wordList.value.sortedBy { it.meanings.joinToString() }
            } else {
                _wordList.value.sortedWith(compareBy<Word> {
                    it.headword.contains(searchText.value, ignoreCase = true)
                }.thenBy {
                    it.meanings.joinToString()
                })
                //_wordList.value.sortedByDescending { it.meanings.joinToString() }
            }
            "Right" -> if (sortOrder.value == "Ascending") {
                _wordList.value.sortedWith(compareBy<Word> {
                    !it.headword.contains(searchText.value, ignoreCase = true)
                }.thenBy {
                    it.right
                })
                //_wordList.value.sortedBy { it.right }
            } else {
                _wordList.value.sortedWith(compareBy<Word> {
                    it.headword.contains(searchText.value, ignoreCase = true)
                }.thenBy {
                    it.right
                })
                //_wordList.value.sortedByDescending { it.right }
            }
            "Wrong" -> if (sortOrder.value == "Ascending") {
                _wordList.value.sortedWith(compareBy<Word> {
                    !it.headword.contains(searchText.value, ignoreCase = true)
                }.thenBy {
                    it.wrong
                })
                //_wordList.value.sortedBy { it.wrong }
            } else {
                _wordList.value.sortedWith(compareBy<Word> {
                    it.headword.contains(searchText.value, ignoreCase = true)
                }.thenBy {
                    it.wrong
                })
                //_wordList.value.sortedByDescending { it.wrong }
            }
            else -> _wordList.value
        }
        _wordList.value = sortedList
    }

    fun getTotalWordCount() : Int {
        var totalCount = 0
        _vocList.value.forEach {
            totalCount += it.word_count
        }
        return totalCount
    }

    // 프로필 화면 total right count
    fun getTotalRightCount() : Int {
        var totalCount = 0
        _vocList.value.forEach {voc ->
            voc.words_json.forEach {
                totalCount += it.right
            }
        }
        return totalCount
    }

    // 프로필 화면 total wrong count
    fun getTotalWrongCount() : Int {
        var totalCount = 0
        _vocList.value.forEach {voc ->
            voc.words_json.forEach {
                totalCount += it.wrong
            }
        }
        return totalCount
    }

    // 기본 단어장을 저장하는 함수
    fun addDefaultVoc(userId: Int) {
        val defaultWordString : String = """
        |considering that^～을 고려하건대
        |frankly speaking^솔직히 말하여
        |consider^고려하다^간주하다
        |consideration^고려^생각
        |considerable^상당한
        |last^마지막^지난 계속하다
        |society^사회
        |social^사회적인
        |sociable^사교적인
        |socialize^사회화하다
        |statement^진술
        |state^상태^진술하다
        |explain^설명하다
        |explanation^설명
        |rib^갈비뼈
        |instead of^～대신에
        |in the place of^～대신에
        |invent^발명하다^만들다
        |invention^발명
        |female^여성의
        |male^남성의
        |in the first place^처음으로
        |at first^처음에는
        |inheritance^유전^유산
        |inherit^상속하다^이어받다
        |symbol^징표
        |symbolic^상징적인
        |castle^성
        |reinforce^강화시키다
        |religious^종교적인
        |religion^종교
        |region^지역
        |regional^지역적인
        |train^기차^훈련시키다
        |training^훈련
        |trainer^훈련자
        |educational^교육적인
        |education^교육
        |press^누르다^언론^출판
        |oppress^억압하다
        |oppression^억압
        |suppress^저하시키다
        |suppression^저하^의기소침
        |law^법
        |lawful^법적인
        |legal^합법적인
        |illegal^불법적인
        |recently^최근에
        |be filled with^～ 로 가득차 있다
        |contented^만족한
        """.trimMargin()

        var wordCount = 0
        getNextAvailableVocId(onComplete = { availableVocId ->
            val defaultWordList = defaultWordString.lines().map { line ->
                val parts = line.split("^")
                val headword = parts[0].trim()
                val meanings = parts.drop(1).map { it.trim() }
                wordCount += 1

                Word(
                    word_id = wordCount,
                    voc_id = availableVocId,
                    headword = headword,
                    lang = "en",
                    meanings = meanings,
                    create_date = getCurrentDateTime())
            }

            var defaultVoc : Voc = Voc(
                user_id = userId,
                voc_id = availableVocId,
                title = "기본 단어장",
                description = "기본적으로 제공되는 영어 단어장입니다. 평가하실 때 사용해주세요!",
                lang = "en",
                word_count = wordCount,
                words_json = defaultWordList,
                create_date = getCurrentDateTime())


            viewModelScope.launch {
                val result = vocRepository.addVoc(defaultVoc)
                if (result) {
                    loadVocs(userId)
                }
            }
        })
    }

    fun createQuiz(vocId: Int, quizStyle: Int): Quiz {
        var words = _wordList.value.filter { it.voc_id == vocId }

        // 0: "완전 랜덤", 1: "틀린 단어 위주", 2: "객관식", 3: "주관식"
        if(quizStyle == 1) { // 틀린 단어 위주
            val totalWrong = words.sumOf { it.wrong }
            val wrongMean : Double = if (words.isNotEmpty()) {
                totalWrong.toDouble() / words.size
            } else {
                0.0
            }
            words = words.filter { it.wrong >= wrongMean }
        }

        var multipleOrShort = Random.nextInt(2) // 객관식 또는 주관식
        val multipleSelectType = Random.nextInt(2) // 객관식의 타입
        val shortAnswerType = Random.nextInt(3) // 주관식의 타입

        // 퀴즈 스타일: 2이면 객관식, 3이면 주관식
        if(quizStyle == 2 || quizStyle == 3) multipleOrShort = quizStyle - 2

        return if (words.isNotEmpty()) {
            val randomWord = words.random() // 문제로 출제할 단어

            when(multipleOrShort) {
                0 -> {
                    when(multipleSelectType) {
                        0 -> {
                            val options = (words - randomWord).shuffled().take(3).toMutableList().apply {
                                add(randomWord)
                                shuffle()
                            }.map { it.headword }
                            Quiz(
                                question = "다음 중 \"${randomWord.meanings.joinToString(", ")}\"를 의미하는 단어는 무엇인지 고르시오.",
                                multipleOrShort = multipleOrShort,
                                multipleSelectType = multipleSelectType,
                                options = options,
                                correctAnswer = randomWord.headword
                            )
                        }
                        1 -> {
                            val randomWordMeaning = randomWord.meanings.random()
                            var options = (words - randomWord).shuffled().take(3).toMutableList().map { it.meanings.random() }

                            options = options.toMutableList().apply {
                                add(randomWordMeaning)
                                shuffle()
                            }

                            Quiz(
                                question = "다음 중 \"${randomWord.headword}\"의 한글 뜻을 고르시오.",
                                multipleOrShort = multipleOrShort,
                                multipleSelectType = multipleSelectType,
                                options = options,
                                correctAnswer = randomWordMeaning
                            )
                        }
                        else -> {
                            Quiz(question = "", options = emptyList(), correctAnswer = "")
                        }
                    }
                }
                1 -> {
                    when(shortAnswerType) {
                        0 -> {
                            Quiz(
                                question = "\"${randomWord.meanings.joinToString(", ")}\"을 의미하는 단어의 표제어를 적으시오.",
                                multipleOrShort = multipleOrShort,
                                multipleSelectType = multipleSelectType,
                                options = emptyList(),
                                correctAnswer = randomWord.headword
                            )
                        }
                        1 -> {
                            val cleanHeadword = randomWord.headword.replace(" ","")
                            val randomAlphabet = cleanHeadword[Random.nextInt(cleanHeadword.length)]
                            Quiz(
                                question = "\"${randomWord.meanings.joinToString(", ")}\"을 의미하는 단어에서 '${randomAlphabet}'의 개수를 적으시오.",
                                multipleOrShort = multipleOrShort,
                                multipleSelectType = multipleSelectType,
                                options = emptyList(),
                                correctAnswer = randomWord.headword.count{it == randomAlphabet}.toString()
                            )
                        }
                        2 -> {
                            Quiz(
                                question = "\"${randomWord.meanings.joinToString(", ")}\"을 의미하는 단어의 시작하는 알파벳을 적으시오.",
                                multipleOrShort = multipleOrShort,
                                multipleSelectType = multipleSelectType,
                                options = emptyList(),
                                correctAnswer = randomWord.headword[0].toString()
                            )
                        }
                        else -> {
                            Quiz(question = "", options = emptyList(), correctAnswer = "")
                        }
                    }
                }
                else -> {
                    Quiz(question = "", options = emptyList(), correctAnswer = "")
                }
            }
        } else {
            Quiz(question = "", options = emptyList(), correctAnswer = "")
        }
    }
}
