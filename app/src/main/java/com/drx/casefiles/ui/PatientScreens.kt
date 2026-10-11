@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.drx.casefiles.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.drx.casefiles.R
import com.drx.casefiles.data.Admission
import com.drx.casefiles.data.Media
import com.drx.casefiles.data.Patient
import com.drx.casefiles.data.Repo
import com.drx.casefiles.data.Visit
import com.drx.casefiles.data.admissionLabels
import com.drx.casefiles.data.dayNumber
import com.drx.casefiles.data.visitLabels
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(repo: Repo, nav: Nav) {
    val scope = rememberCoroutineScope()
    val patients by remember { repo.dao.patients() }.collectAsState(emptyList())
    val media by remember { repo.dao.allMedia() }.collectAsState(emptyList())
    val visits by remember { repo.dao.allVisits() }.collectAsState(emptyList())
    var q by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Patient?>(null) }

    val thumbs = remember(media) { media.groupBy { it.patientId }.mapValues { it.value.first() } }
    val kwPatients = remember(visits, q) {
        val w = q.trim().trimStart('#').lowercase()
        if (w.isBlank()) emptySet()
        else visits.filter { v -> v.keywords.split("|").any { it.lowercase().contains(w) } }.map { it.patientId }.toSet()
    }
    val filtered = patients.filter {
        q.isBlank() || it.name.contains(q, true) || it.phone.contains(q, true) ||
            it.complaint.contains(q, true) || it.diagnosis.contains(q, true) || it.id in kwPatients
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { nav.push(Screen.Search) }) { Icon(Icons.Default.Search, null) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null) }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                q, { q = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.search_patients)) },
                modifier = Modifier.fillMaxWidth().padding(12.dp, 4.dp)
            )
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_patients), textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                }
            } else {
                LazyColumn {
                    items(filtered, key = { it.id }) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { nav.push(Screen.PatientDetail(p.id)) },
                                    onLongClick = { toDelete = p }
                                )
                                .padding(12.dp, 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                val t = thumbs[p.id]
                                if (t != null) {
                                    AsyncImage(repo.file(t.path), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                val sub = p.diagnosis.ifBlank { p.complaint }
                                if (sub.isNotBlank()) {
                                    Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAdd) {
        TextInputDialog(
            stringResource(R.string.new_patient), stringResource(R.string.patient_name),
            onConfirm = { name ->
                showAdd = false
                scope.launch {
                    val id = repo.dao.insertPatient(Patient(name = name))
                    repo.dao.insertVisit(Visit(patientId = id, kind = "INITIAL"))
                    nav.push(Screen.PatientDetail(id))
                }
            },
            onDismiss = { showAdd = false }
        )
    }
    toDelete?.let { p ->
        ConfirmDialog(
            stringResource(R.string.delete_patient_q),
            onConfirm = { toDelete = null; scope.launch { repo.deletePatient(p) } },
            onDismiss = { toDelete = null }
        )
    }
}

private data class AddTarget(val admissionId: Long?)

@Composable
fun PatientScreen(id: Long, repo: Repo, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dbPatient by remember(id) { repo.dao.patient(id) }.collectAsState(null)
    val visits by remember(id) { repo.dao.visits(id) }.collectAsState(emptyList())
    val media by remember(id) { repo.dao.mediaOfPatientFlow(id) }.collectAsState(emptyList())
    val admissions by remember(id) { repo.dao.admissions(id) }.collectAsState(emptyList())

    var f by remember { mutableStateOf<Patient?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var addTarget by remember { mutableStateOf<AddTarget?>(null) }
    var visitToDelete by remember { mutableStateOf<Visit?>(null) }
    var admToDelete by remember { mutableStateOf<Admission?>(null) }
    var dateTarget by remember { mutableStateOf<Pair<Admission, Boolean>?>(null) }
    var reasonTarget by remember { mutableStateOf<Admission?>(null) }
    var showNewAdmission by remember { mutableStateOf(false) }

    LaunchedEffect(dbPatient) { if (f == null && dbPatient != null) f = dbPatient }
    LaunchedEffect(f) {
        if (dirty) {
            delay(600)
            f?.let { repo.dao.updatePatient(it.copy(updatedAt = System.currentTimeMillis())) }
            dirty = false
        }
    }
    val latest by rememberUpdatedState(f)
    val latestDirty by rememberUpdatedState(dirty)
    DisposableEffect(Unit) {
        onDispose {
            if (latestDirty) latest?.let { repo.appScope.launch { repo.dao.updatePatient(it) } }
        }
    }
    fun edit(n: Patient) {
        f = n
        dirty = true
    }

    val labels = remember(visits) { visitLabels(ctx, visits) }
    val admLabels = remember(admissions) { admissionLabels(ctx, admissions) }
    val dateFmt = remember { DateFormat.getDateInstance() }
    val initialVisit = visits.firstOrNull { it.kind == "INITIAL" }
    val lastVisit = visits.maxByOrNull { it.date }
    val openAdmission = admissions.filter { it.dischargeDate == null }.maxByOrNull { it.admitDate }

    @Composable
    fun RenderVisit(v: Visit, indent: Boolean) {
        val vm: List<Media> = media.filter { it.visitId == v.id }
        Box(Modifier.padding(start = if (indent) 16.dp else 0.dp)) {
            VisitBox(
                v = v,
                label = labels[v.id].orEmpty(),
                day = stringResource(R.string.day_fmt, dayNumber(v, visits)),
                date = dateFmt.format(Date(v.date)),
                thumbs = vm.takeLast(4).reversed(),
                repo = repo,
                onClick = { nav.push(Screen.VisitDetail(v.id)) },
                onLong = { if (v.kind != "INITIAL") visitToDelete = v }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(f?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                }
            )
        }
    ) { pad ->
        val p = f
        if (p == null) {
            Box(Modifier.padding(pad).fillMaxSize())
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormField(stringResource(R.string.patient_name), p.name, Modifier.weight(2f)) { edit(p.copy(name = it)) }
                        FormField(stringResource(R.string.age), p.age, Modifier.weight(1f)) { edit(p.copy(age = it)) }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormField(stringResource(R.string.residence), p.residence, Modifier.weight(1f)) { edit(p.copy(residence = it)) }
                        FormField(stringResource(R.string.phone), p.phone, Modifier.weight(1f)) { edit(p.copy(phone = it)) }
                    }
                }
                item { Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                item { FormField(stringResource(R.string.complaint), p.complaint) { edit(p.copy(complaint = it)) } }
                item { FormField(stringResource(R.string.pmh), p.pmh) { edit(p.copy(pmh = it)) } }
                item { FormField(stringResource(R.string.psh), p.psh) { edit(p.copy(psh = it)) } }
                item { FormField(stringResource(R.string.drugs), p.drugs) { edit(p.copy(drugs = it)) } }
                item { FormField(stringResource(R.string.allergy), p.allergy) { edit(p.copy(allergy = it)) } }
                item { FormField(stringResource(R.string.family), p.family) { edit(p.copy(family = it)) } }
                item { FormField(stringResource(R.string.diagnosis), p.diagnosis) { edit(p.copy(diagnosis = it)) } }
                item { FormField(stringResource(R.string.description), p.description, last = true) { edit(p.copy(description = it)) } }

                // The two most important boxes: first day and latest visit.
                if (initialVisit != null) {
                    item(key = "first") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.first_day_card), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                            RenderVisit(initialVisit, false)
                        }
                    }
                }
                if (lastVisit != null && lastVisit.id != initialVisit?.id) {
                    item(key = "last") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.last_visit_card), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                            RenderVisit(lastVisit, false)
                        }
                    }
                }

                // Admissions: each one is a parent folder holding its visits.
                item(key = "adm_header") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.admissions), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showNewAdmission = true }) { Icon(Icons.Default.Add, null) }
                    }
                }
                admissions.forEach { a ->
                    item(key = "adm" + a.id) {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(admLabels[a.id].orEmpty(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { addTarget = AddTarget(a.id) }) { Icon(Icons.Default.Add, null) }
                                    IconButton(onClick = { admToDelete = a }) { Icon(Icons.Default.Delete, null) }
                                }
                                Row {
                                    Text(
                                        stringResource(R.string.admit_date) + ": " + dateFmt.format(Date(a.admitDate)),
                                        modifier = Modifier.weight(1f).clickable { dateTarget = a to true }.padding(vertical = 6.dp)
                                    )
                                    Text(
                                        stringResource(R.string.discharge_date) + ": " +
                                            (a.dischargeDate?.let { dateFmt.format(Date(it)) } ?: stringResource(R.string.not_set)),
                                        modifier = Modifier.weight(1f).clickable { dateTarget = a to false }.padding(vertical = 6.dp)
                                    )
                                }
                                Text(
                                    a.reason.ifBlank { stringResource(R.string.admission_reason) },
                                    modifier = Modifier.fillMaxWidth().clickable { reasonTarget = a }.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                    items(visits.filter { it.admissionId == a.id }, key = { "v" + it.id }) { v -> RenderVisit(v, true) }
                }

                val standalone = visits.filter { it.admissionId == null }
                item(key = "visits_header") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.standalone_visits), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { addTarget = AddTarget(openAdmission?.id) }) { Icon(Icons.Default.Add, null) }
                    }
                }
                items(standalone, key = { "v" + it.id }) { v -> RenderVisit(v, false) }
            }
        }
    }

    addTarget?.let { t ->
        val opts = buildList {
            if (t.admissionId != null) {
                add("AM" to stringResource(R.string.visit_am))
                add("PM" to stringResource(R.string.visit_pm))
            }
            add("CLINIC" to stringResource(R.string.visit_type_clinic))
        }
        ChoiceDialog(
            title = stringResource(R.string.add_visit),
            options = opts,
            customHint = stringResource(R.string.visit_name_hint),
            onPick = { key ->
                addTarget = null
                scope.launch {
                    val v = if (key == "AM" || key == "PM" || key == "CLINIC") {
                        Visit(patientId = id, admissionId = t.admissionId, kind = key)
                    } else {
                        Visit(patientId = id, admissionId = t.admissionId, kind = "CUSTOM", title = key)
                    }
                    val vid = repo.dao.insertVisit(v)
                    nav.push(Screen.VisitDetail(vid))
                }
            },
            onDismiss = { addTarget = null }
        )
    }
    visitToDelete?.let { v ->
        ConfirmDialog(
            stringResource(R.string.delete_visit_q),
            onConfirm = { visitToDelete = null; scope.launch { repo.deleteVisit(v) } },
            onDismiss = { visitToDelete = null }
        )
    }
    admToDelete?.let { a ->
        ConfirmDialog(
            stringResource(R.string.delete_admission_q),
            onConfirm = { admToDelete = null; scope.launch { repo.dao.deleteAdmission(a) } },
            onDismiss = { admToDelete = null }
        )
    }
    dateTarget?.let { (a, isAdmit) ->
        DatePickDialog(
            initial = if (isAdmit) a.admitDate else a.dischargeDate,
            onPick = { d ->
                dateTarget = null
                scope.launch {
                    repo.dao.updateAdmission(if (isAdmit) a.copy(admitDate = d) else a.copy(dischargeDate = d))
                }
            },
            onDismiss = { dateTarget = null }
        )
    }
    reasonTarget?.let { a ->
        TextInputDialog(
            stringResource(R.string.admission_reason), stringResource(R.string.admission_reason),
            onConfirm = { r -> reasonTarget = null; scope.launch { repo.dao.updateAdmission(a.copy(reason = r)) } },
            onDismiss = { reasonTarget = null },
            initial = a.reason
        )
    }
    if (showNewAdmission) {
        TextInputDialog(
            stringResource(R.string.new_admission), stringResource(R.string.admission_reason),
            onConfirm = { r ->
                showNewAdmission = false
                scope.launch {
                    val admId = repo.dao.insertAdmission(Admission(patientId = id, reason = r))
                    // A patient who only has the first-day history joins that visit to the new admission.
                    val onlyInitial = visits.size == 1 && visits[0].kind == "INITIAL" && visits[0].admissionId == null
                    if (onlyInitial) repo.dao.updateVisit(visits[0].copy(admissionId = admId))
                    else repo.dao.insertVisit(Visit(patientId = id, admissionId = admId, kind = "ADMIT"))
                }
            },
            onDismiss = { showNewAdmission = false }
        )
    }
}

@Composable
private fun VisitBox(
    v: Visit,
    label: String,
    day: String,
    date: String,
    thumbs: List<Media>,
    repo: Repo,
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("$day · $date", style = MaterialTheme.typography.bodySmall)
            }
            if (v.notes.isNotBlank()) {
                Text(v.notes, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (v.keywords.isNotBlank()) {
                Text(
                    v.keywords.split("|").filter { it.isNotBlank() }.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (thumbs.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    thumbs.forEach { m ->
                        AsyncImage(
                            repo.file(m.path), null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
        }
    }
}
