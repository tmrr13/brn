package com.epam.brn.service

import com.epam.brn.constant.BrnRoles.AUTH_ROLE_ADMIN
import com.epam.brn.constant.BrnRoles.AUTH_ROLE_USER
import com.epam.brn.constant.ExerciseTypeEnum
import com.epam.brn.constant.WordTypeEnum
import com.epam.brn.csv.CsvMappingIteratorParser
import com.epam.brn.csv.converter.impl.firstSeries.ExerciseCsvConverter
import com.epam.brn.csv.converter.impl.firstSeries.GroupCsvConverter
import com.epam.brn.csv.converter.impl.firstSeries.SeriesCsvConverter
import com.epam.brn.csv.converter.impl.firstSeries.TaskCsv1SeriesConverter
import com.epam.brn.csv.converter.impl.secondSeries.Exercise2SeriesConverter
import com.epam.brn.csv.firstSeries.TaskCSVParser1SeriesService
import com.epam.brn.csv.firstSeries.commaSeparated.CommaSeparatedExerciseCSVParserService
import com.epam.brn.csv.firstSeries.commaSeparated.CommaSeparatedGroupCSVParserService
import com.epam.brn.csv.firstSeries.commaSeparated.CommaSeparatedSeriesCSVParserService
import com.epam.brn.csv.secondSeries.CSVParser2SeriesService
import com.epam.brn.model.Authority
import com.epam.brn.model.Exercise
import com.epam.brn.model.Resource
import com.epam.brn.model.Task
import com.epam.brn.model.UserAccount
import com.epam.brn.repo.ExerciseGroupRepository
import com.epam.brn.repo.ExerciseRepository
import com.epam.brn.repo.SeriesRepository
import com.epam.brn.repo.TaskRepository
import com.epam.brn.repo.UserAccountRepository
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import org.apache.logging.log4j.kotlin.logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.core.io.ResourceLoader
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

// TODO: this class smells like GOD-object anti-pattern, it should be split

/**
 * This class is responsible for
 * loading seed data on startup.
 */
@Service
@Profile("dev", "prod")
class InitialDataLoader(
    private val resourceLoader: ResourceLoader,
    private val exerciseGroupRepository: ExerciseGroupRepository,
    private val seriesRepository: SeriesRepository,
    private val exerciseRepository: ExerciseRepository,
    private val taskRepository: TaskRepository,
    private val userAccountRepository: UserAccountRepository,
    private val csvMappingIteratorParser: CsvMappingIteratorParser,
    private val passwordEncoder: PasswordEncoder,
    private val authorityService: AuthorityService
) {
    private val log = logger()

    @Value("\${init.folder:#{null}}")
    var directoryPath: Path? = null

    @Autowired
    lateinit var groupCsvConverter: GroupCsvConverter

    @Autowired
    lateinit var exerciseCsvConverter: ExerciseCsvConverter

    @Autowired
    lateinit var seriesCsvConverter: SeriesCsvConverter

    @Autowired
    lateinit var taskCsv1SeriesConverter: TaskCsv1SeriesConverter

    @Autowired
    lateinit var exercise2SeriesConverter: Exercise2SeriesConverter

    @Autowired
    lateinit var seriesService: SeriesService

    @Autowired
    lateinit var resourceService: ResourceService

    companion object {
        fun fileNameForSeries(seriesId: Long) = "${seriesId}_series.csv"

        private const val GROUPS_FILE = "groups.csv"
        private const val SERIES_FILE = "series.csv"
        private const val EXERCISES_FILE = "exercises.csv"

        private val sourceFiles = listOf(
            GROUPS_FILE,
            SERIES_FILE,
            EXERCISES_FILE,
            fileNameForSeries(1),
            fileNameForSeries(2),
            fileNameForSeries(3)
        )
    }

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationEvent(event: ApplicationReadyEvent) {
        val adminAuthority = authorityService.save(Authority(authorityName = AUTH_ROLE_ADMIN))
        val userAuthority = authorityService.save(Authority(authorityName = AUTH_ROLE_USER))
        val admin = addAdminUser(adminAuthority)
        val listOfUsers = addDefaultUsers(userAuthority)
        listOfUsers.add(admin)

        userAccountRepository.saveAll(listOfUsers)

        if (isInitRequired()) {
            init()
        }
    }

    private fun isInitRequired() = exerciseGroupRepository.count() == 0L

    private fun init() {
        log.debug("Initialization started")

        if (directoryPath != null) {
            initDataFromDirectory(directoryPath!!)
        } else {
            initDataFromClassPath()
        }
    }

    private fun addDefaultUsers(userAuthority: Authority): MutableList<UserAccount> {
        val password = passwordEncoder.encode("password")
        val firstUser = UserAccount(
            firstName = "firstName",
            lastName = "lastName",
            email = "default@default.ru",
            active = true,
            password = password
        )
        val secondUser = UserAccount(
            firstName = "firstName2",
            lastName = "lastName2",
            email = "default2@default.ru",
            active = true,
            password = password
        )
        firstUser.authoritySet.addAll(setOf(userAuthority))
        secondUser.authoritySet.addAll(setOf(userAuthority))
        return mutableListOf(firstUser, secondUser)
    }

    private fun addAdminUser(adminAuthority: Authority): UserAccount {
        val password = passwordEncoder.encode("admin")
        val userAccount =
            UserAccount(
                firstName = "admin",
                lastName = "admin",
                password = password,
                email = "admin@admin.com",
                active = true
            )
        userAccount.authoritySet.addAll(setOf(adminAuthority))
        return userAccount
    }

    private fun initDataFromDirectory(directoryToScan: Path) {
        log.debug("Loading data from $directoryToScan.")

        if (!Files.exists(directoryToScan) || !Files.isDirectory(directoryPath))
            throw IllegalArgumentException("$directoryToScan with initial data does not exist")

        val sources = sourceFiles
            .map { Pair(it, Files.newInputStream(directoryToScan.resolve(it))) }
            .toMap()

        loadDataToDb(sources)
    }

    private fun initDataFromClassPath() {
        log.debug("Loading data from classpath 'initFiles' directory.")

        val sources = sourceFiles
            .map { Pair(it, resourceLoader.getResource("classpath:initFiles/$it").inputStream) }
            .toMap()

        loadDataToDb(sources)
    }

    private fun loadDataToDb(sources: Map<String, InputStream>) {
        loadFromInputStream(sources.getValue(GROUPS_FILE), ::loadExerciseGroups)
        loadFromInputStream(sources.getValue(SERIES_FILE), ::loadSeries)
        loadFromInputStream(sources.getValue(EXERCISES_FILE), ::loadExercises)
        loadFromInputStream(sources.getValue(fileNameForSeries(1)), ::loadTasksFor1Series)
        loadFromInputStream(sources.getValue(fileNameForSeries(2)), ::loadTasksFor2Series)
        loadFromInputStream(sources.getValue(fileNameForSeries(3)), ::loadExercisesFor3Series)
        log.debug("Initialization succeeded")
    }

    private fun loadFromInputStream(inputStream: InputStream, load: (inputStream: InputStream) -> Unit) {
        try {
            load(inputStream)
        } finally {
            closeSilently(inputStream)
        }
    }

    private fun closeSilently(inputStream: InputStream) {
        try {
            inputStream.close()
        } catch (e: Exception) {
            log.error(e)
        }
    }

    private fun loadExerciseGroups(inputStream: InputStream) {
        val groups = csvMappingIteratorParser
            .parseCsvFile(inputStream, groupCsvConverter, CommaSeparatedGroupCSVParserService())

        exerciseGroupRepository.saveAll(groups)
    }

    private fun loadSeries(inputStream: InputStream) {
        val series = csvMappingIteratorParser
            .parseCsvFile(inputStream, seriesCsvConverter, CommaSeparatedSeriesCSVParserService())

        seriesRepository.saveAll(series)
    }

    private fun loadExercises(inputStream: InputStream) {
        val exercises = csvMappingIteratorParser
            .parseCsvFile(inputStream, exerciseCsvConverter, CommaSeparatedExerciseCSVParserService())

        exerciseRepository.saveAll(exercises)
    }

    private fun loadTasksFor1Series(tasksInputStream: InputStream) {
        val tasks = csvMappingIteratorParser
            .parseCsvFile(tasksInputStream, taskCsv1SeriesConverter, TaskCSVParser1SeriesService())

        taskRepository.saveAll(tasks)
    }

    private fun loadTasksFor2Series(inputStream: InputStream) {
        val exercises = csvMappingIteratorParser
            .parseCsvFile(inputStream, exercise2SeriesConverter, CSVParser2SeriesService())

        exerciseRepository.saveAll(exercises)
    }

    private fun loadExercisesFor3Series(inputStream: InputStream) {
        // todo: get data from file for 3 series
        val exercises = createExercises()

        exerciseRepository.saveAll(exercises)
    }

    private fun createExercises(): List<Exercise> {
        val exercise = createExercise()
        val task = createTask()

        exercise.addTask(task)
        task.exercise = exercise

        seriesService.findSeriesWithExercisesForId(3L).exercises.add(exercise)

        return listOf(exercise)
    }

    private fun createExercise(): Exercise {
        return Exercise(
            series = seriesService.findSeriesForId(3L),
            name = "Распознование предложений из 2 слов",
            description = "Распознование предложений из 2 слов",
            template = "<OBJECT OBJECT_ACTION>",
            exerciseType = ExerciseTypeEnum.SENTENCE.toString(),
            level = 1
        )
    }

    private fun createTask(): Task {
        val resource1 = Resource(
            word = "девочкаTest",
            wordType = WordTypeEnum.OBJECT.toString(),
            audioFileUrl = "series2/девочка.mp3",
            pictureFileUrl = "pictures/withWord/девочка.jpg"
        )
        val resource2 = Resource(
            word = "дедушкаTest",
            wordType = WordTypeEnum.OBJECT.toString(),
            audioFileUrl = "series2/дедушка.mp3",
            pictureFileUrl = "pictures/withWord/дедушка.jpg"
        )
        val resource3 = Resource(
            word = "бабушкаTest",
            wordType = WordTypeEnum.OBJECT.toString(),
            audioFileUrl = "series2/бабушка.mp3",
            pictureFileUrl = "pictures/withWord/бабушка.jpg"
        )
        val resource4 = Resource(
            word = "бросаетTest",
            wordType = WordTypeEnum.OBJECT_ACTION.toString(),
            audioFileUrl = "series2/бросает.mp3",
            pictureFileUrl = "pictures/withWord/бросает.jpg"
        )
        val resource5 = Resource(
            word = "читаетTest",
            wordType = WordTypeEnum.OBJECT_ACTION.toString(),
            audioFileUrl = "series2/читает.mp3",
            pictureFileUrl = "pictures/withWord/читает.jpg"
        )
        val resource6 = Resource(
            word = "рисуетTest",
            wordType = WordTypeEnum.OBJECT_ACTION.toString(),
            audioFileUrl = "series2/рисует.mp3",
            pictureFileUrl = "pictures/withWord/рисует.jpg"
        )

        val answerOptions =
            mutableSetOf(resource1, resource2, resource3, resource4, resource5, resource6)

        val correctAnswer = Resource(
            word = "девочка рисует",
            wordType = WordTypeEnum.SENTENCE.toString(),
            audioFileUrl = "series3/девочка_рисует.mp3"
        )

        resourceService.saveAll(answerOptions)
        resourceService.save(correctAnswer)

        return Task(
            serialNumber = 2,
            answerOptions = answerOptions,
            correctAnswer = correctAnswer,
            answerParts = mutableMapOf(1 to resource1, 2 to resource6)
        )
    }
}
