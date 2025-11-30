package com.localdoorhub.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.localdoorhub.app.R
import com.localdoorhub.app.data.DoorbellRepository
import com.localdoorhub.app.data.local.PreferencesManager
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    repo: DoorbellRepository = DoorbellRepository(),
    prefsManager: PreferencesManager = PreferencesManager(LocalContext.current)
) {
    var step by remember { mutableStateOf(0) }
    val totalSteps = 5

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize()
        ) {
            StepIndicator(current = step, total = totalSteps)

            Spacer(modifier = Modifier.height(24.dp))

            when (step) {
                0 -> StepWelcome(
                    onNext = { step++ }
                )
                1 -> StepWifi(
                    onNext = { step++ },
                    onBack = { step-- }
                )
                2 -> StepDetectDoorbell(
                    repo = repo,
                    onNext = { step++ },
                    onBack = { step-- }
                )
                3 -> StepTestChimeVideo(
                    repo = repo,
                    onNext = { step++ },
                    onBack = { step-- }
                )
                4 -> StepDoorName(
                    repo = repo,
                    prefsManager = prefsManager,
                    onFinish = {
                        onFinished()
                    },
                    onBack = { step-- }
                )
            }
        }
    }
}

@Composable
fun StepIndicator(current: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(total) { index ->
            val color = if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = color,
                    shape = CircleShape,
                    modifier = Modifier.size(10.dp)
                ) {}
            }
        }
    }
}

@Composable
fun StepWelcome(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.welcome_description))
        }
        Button(
            onClick = onNext,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.get_started))
        }
    }
}

@Composable
fun StepWifi(onNext: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = stringResource(R.string.wifi_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.wifi_description))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back))
            }
            Button(onClick = onNext) {
                Text(stringResource(R.string.im_connected))
            }
        }
    }
}

@Composable
fun StepDetectDoorbell(
    repo: DoorbellRepository,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val checkingText = stringResource(R.string.checking_doorbell)
    val notFoundText = stringResource(R.string.doorbell_not_found)
    val doorbellFoundFormat = stringResource(R.string.doorbell_found)
    var statusText by remember { mutableStateOf(checkingText) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val status = repo.getStatus()
        statusText = if (status.doorbellOnline) {
            java.lang.String.format(doorbellFoundFormat, status.doorName ?: "Front Door")
        } else {
            notFoundText
        }
        isLoading = false
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = stringResource(R.string.detect_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            if (isLoading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
            }
            Text(statusText)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back))
            }
            Button(onClick = onNext, enabled = !isLoading) {
                Text(stringResource(R.string.next))
            }
        }
    }
}

@Composable
fun StepTestChimeVideo(
    repo: DoorbellRepository,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    var testResult by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val chimeTestSentText = stringResource(R.string.chime_test_sent)
    val chimeTestFailedText = stringResource(R.string.chime_test_failed)
    val videoTestSucceededText = stringResource(R.string.video_test_succeeded)
    val videoTestFailedText = stringResource(R.string.video_test_failed)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = stringResource(R.string.test_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.test_description))
            Spacer(Modifier.height(16.dp))
            Row {
                Button(onClick = {
                    testResult = ""
                    scope.launch {
                        val ok = repo.testChime()
                        testResult = if (ok) {
                            chimeTestSentText
                        } else {
                            chimeTestFailedText
                        }
                    }
                }) {
                    Text(stringResource(R.string.test_chime))
                }
                Spacer(Modifier.width(16.dp))
                Button(onClick = {
                    testResult = ""
                    scope.launch {
                        val ok = repo.testStream()
                        testResult = if (ok) {
                            videoTestSucceededText
                        } else {
                            videoTestFailedText
                        }
                    }
                }) {
                    Text(stringResource(R.string.test_video))
                }
            }
            if (testResult.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(testResult)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back))
            }
            Button(onClick = onNext) {
                Text(stringResource(R.string.next))
            }
        }
    }
}

@Composable
fun StepDoorName(
    repo: DoorbellRepository,
    prefsManager: PreferencesManager,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    var text by remember { mutableStateOf(TextFieldValue(prefsManager.getDoorName() ?: "Front Door")) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = stringResource(R.string.door_name_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.door_name_description))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.door_name_hint)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back))
            }
            Button(onClick = {
                scope.launch {
                    repo.finishSetup(text.text)
                    prefsManager.setDoorName(text.text)
                    onFinish()
                }
            }) {
                Text(stringResource(R.string.finish_setup))
            }
        }
    }
}

