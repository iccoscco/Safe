package com.example.appgestin.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.example.appgestin.model.LocalProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appgestin.BuildConfig
import com.example.appgestin.controller.AppState
import com.example.appgestin.controller.GstiController
import com.example.appgestin.controller.Page
import com.example.appgestin.model.EmaPrompt
import com.example.appgestin.model.HealthConnectCollector
import com.example.appgestin.model.Questionnaire
import com.example.appgestin.model.Questionnaires
import com.example.appgestin.model.StudyInstrument
import com.example.appgestin.model.StudyMetric
import com.example.appgestin.model.isAvailable
import com.example.appgestin.model.timestampNow

import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

private val ink = Color(0xFF122343)
private val muted = Color(0xFF61718B)
private val indigo = Color(0xFF0866D9)
private val teal = Color(0xFF358FEF)
private val glassShape = RoundedCornerShape(26.dp)

@Composable
fun GstiView(state: AppState, controller: GstiController) {
    var chatOpen by rememberSaveable { mutableStateOf(false) }
    val mainPage = state.page !in listOf(Page.Login, Page.Enroll)
    LaunchedEffect(state.page) { if (state.page == Page.Login) chatOpen = false }
    BackHandler(state.page == Page.Enroll || mainPage && (chatOpen || state.page != Page.Home)) {
        chatOpen = false
        if (state.page == Page.Enroll) controller.closeEnrollment() else controller.goHome()
    }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFF9FCFF), Color(0xFFE4F2FF))))) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0x1870B8FF), radius = size.width * .69f, center = Offset(size.width * 1.08f, size.height * .04f))
            drawCircle(Color(0x1470B8FF), radius = size.width * .85f, center = Offset(-size.width * .45f, size.height * .60f))
            drawCircle(Color(0x1270B8FF), radius = size.width * .72f, center = Offset(size.width * 1.05f, size.height * .91f))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Safe", color = indigo, fontWeight = FontWeight.Bold, fontSize = 24.sp,
                    modifier = Modifier.weight(1f))
                if (state.page == Page.Enroll) {
                    TextButton(onClick = controller::closeEnrollment) { Text("Volver", fontSize = 12.sp) }
                } else {
                    TextButton(onClick = { chatOpen = false; controller.showEnrollment() }) {
                        Text("Vincular", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (mainPage) {
                    Spacer(Modifier.width(8.dp))
                    ProfileAvatar(state.profile, 38, { chatOpen = false; controller.showSettings() })
                }
            }
            key(state.page, chatOpen) {
                val pageScroll = rememberScrollState()
                val uiScope = rememberCoroutineScope()
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(pageScroll)
                    .padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 22.dp)) {
                    if (chatOpen && mainPage) {
                        ChatPage { chatOpen = false; controller.showResources() }
                    } else when (state.page) {
                        Page.Login -> LoginPage(state.busy, state.error, state.notice, controller::login)
                        Page.Enroll -> EnrollPage(state.busy, state.error, controller::register)
                        Page.Home -> HomeScreen(state, controller) { chatOpen = true }
                        Page.Study -> CatalogScreen(state, controller::openQuestionnaire, controller::showStudy)
                        Page.Questionnaire -> QuestionnairePage(state.selectedQuestionnaire, state.busy,
                            state.error, controller::submitQuestionnaire, controller::showResources,
                            { uiScope.launch { pageScroll.animateScrollTo(0) } })
                        Page.CheckIn -> CheckInPage(state.selectedPrompt, state.busy, state.error,
                            controller::submitCheckIn, controller::showResources)
                        Page.Health -> HealthPage(state.healthAvailable, state.hasPendingSensorBatch,
                            state.busy, state.error, state.notice, state.preview, controller::syncHealth)
                        Page.Resources -> {
                            if (state.error != null) Message(state.error)
                            if (state.notice != null) Message(state.notice, true)
                            if (state.hasPendingResponse) {
                                MainButton("Reintentar envío pendiente", !state.busy, controller::retryPending)
                                Spacer(Modifier.height(14.dp))
                            }
                            ResourcesPage(controller::goHome)
                        }
                        Page.Settings -> ProfileScreen(state, controller)
                    }
                }
            }
            if (mainPage) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth()
                    .shadow(14.dp, glassShape, ambientColor = Color(0x33729AC4), spotColor = Color(0x33729AC4))
                    .clip(glassShape).background(Color.White.copy(alpha = .78f))
                    .border(1.dp, Color.White, glassShape).padding(horizontal = 5.dp, vertical = 7.dp)) {
                    NavItem("Inicio", "home", !chatOpen && state.page == Page.Home, Modifier.weight(1f)) { chatOpen = false; controller.goHome() }
                    NavItem("Cuestionarios", "form", !chatOpen && state.page in listOf(Page.Study, Page.Questionnaire), Modifier.weight(1f)) { chatOpen = false; controller.showStudy() }
                    NavItem("Chat", "chat", chatOpen, Modifier.weight(1f)) { chatOpen = true }
                    NavItem("Perfil", "profile", !chatOpen && state.page == Page.Settings, Modifier.weight(1f)) { chatOpen = false; controller.showSettings() }
                }
            }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: String, selected: Boolean, modifier: Modifier, action: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) Color(0xFFDDEEFF) else Color.Transparent)
        .clickable(onClick = action).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SafeIcon(icon, if (selected) indigo else muted, Modifier.size(23.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (selected) indigo else muted, fontSize = 10.sp, maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Frosted surface over the pale blue background, with a white rim and diffuse relief. */
@Composable
private fun Glass(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.shadow(13.dp, glassShape, ambientColor = Color(0x247CA9D2), spotColor = Color(0x247CA9D2))
        .clip(glassShape).background(Brush.linearGradient(listOf(Color.White.copy(alpha = .83f), Color(0x99ECF6FF))))
        .border(1.dp, Color.White.copy(alpha = .96f), glassShape).padding(18.dp)) { content() }
}

@Composable
private fun MainButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 50.dp).shadow(if (enabled) 9.dp else 0.dp, RoundedCornerShape(25.dp),
        ambientColor = Color(0x44528DD0), spotColor = Color(0x44528DD0)).clip(RoundedCornerShape(25.dp))
        .background(Brush.verticalGradient(if (enabled) listOf(Color(0xFF51A5FA), indigo) else listOf(Color(0xFFD8E4F0), Color(0xFFCCDCEB))))
        .border(1.dp, Color.White.copy(alpha = .65f), RoundedCornerShape(25.dp))
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 15.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (enabled) Color.White else muted, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SoftAction(label: String, modifier: Modifier = Modifier, action: () -> Unit) {
    Box(modifier.shadow(8.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x337AA6D0), spotColor = Color(0x337AA6D0))
        .clip(RoundedCornerShape(22.dp)).background(Brush.verticalGradient(listOf(Color(0xFFF8FCFF), Color(0xFFDDEEFF))))
        .border(1.dp, Color.White, RoundedCornerShape(22.dp)).clickable(onClick = action)
        .padding(horizontal = 17.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
        Text(label, color = indigo, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun IconTile(icon: String, size: Int = 52, onClick: (() -> Unit)? = null) {
    Box(Modifier.size(size.dp).shadow(7.dp, RoundedCornerShape((size / 3).dp),
        ambientColor = Color(0x33789FC4), spotColor = Color(0x33789FC4))
        .clip(RoundedCornerShape((size / 3).dp)).background(Brush.linearGradient(listOf(Color.White, Color(0xFFD9ECFF))))
        .border(1.dp, Color.White, RoundedCornerShape((size / 3).dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), contentAlignment = Alignment.Center) {
        SafeIcon(icon, indigo, Modifier.size((size * .50f).dp))
    }
}

/** Small, consistent outlined symbols; no emoji glyphs or platform-dependent artwork. */
@Composable
private fun SafeIcon(kind: String, color: Color = indigo, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier.semantics { contentDescription = kind }) {
        val u = size.width / 24f
        fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(color, Offset(x*u,y*u), Offset(xx*u,yy*u), 1.6f*u, StrokeCap.Round)
        fun circle(x: Float,y: Float,r: Float) = drawCircle(color,r*u,Offset(x*u,y*u),style=Stroke(1.6f*u))
        fun path(vararg pts: Pair<Float,Float>) {
            val p = Path(); pts.forEachIndexed { i, pt -> if(i==0) p.moveTo(pt.first*u,pt.second*u) else p.lineTo(pt.first*u,pt.second*u) }
            drawPath(p,color,style=Stroke(1.6f*u,cap=StrokeCap.Round))
        }
        when(kind) {
            "home" -> path(3f to 11f,12f to 3f,21f to 11f,21f to 21f,15f to 21f,15f to 14f,9f to 14f,9f to 21f,3f to 21f,3f to 11f)
            "profile", "people" -> { circle(12f,7f,4f); drawArc(color,180f,180f,false,Offset(3*u,14*u),Size(18*u,14*u),style=Stroke(1.6f*u)); if(kind=="people") {circle(3f,9f,2f);circle(21f,9f,2f)} }
            "chat" -> { drawRoundRect(color,Offset(3*u,3*u),Size(18*u,14*u),CornerRadius(5*u),style=Stroke(1.6f*u)); path(8f to 17f,5f to 21f,5f to 16f) }
            "heart" -> { val p=Path(); p.moveTo(12*u,21*u); p.cubicTo(-5*u,10*u,4*u,-3*u,12*u,6*u); p.cubicTo(20*u,-3*u,29*u,10*u,12*u,21*u); drawPath(p,color,style=Stroke(1.6f*u)) }
            "moon" -> { val p=Path();p.moveTo(16*u,3*u);p.cubicTo(-1*u,-1*u,-2*u,25*u,19*u,20*u);p.cubicTo(9*u,20*u,6*u,9*u,16*u,3*u);drawPath(p,color,style=Stroke(1.6f*u)) }
            "arrow" -> {line(4f,12f,20f,12f);path(14f to 6f,20f to 12f,14f to 18f)}
            "clock" -> {circle(12f,12f,9f);path(12f to 6f,12f to 12f,16f to 14f)}
            "help" -> {circle(12f,12f,9f);circle(12f,12f,5f);line(6f,6f,8f,8f);line(16f,16f,18f,18f);line(6f,18f,8f,16f);line(16f,8f,18f,6f)}
            "steps" -> {path(7f to 3f,4f to 10f,4f to 18f,20f to 18f,20f to 14f,12f to 11f,7f to 3f);line(5f,21f,21f,21f)}
            "book" -> {path(12f to 5f,3f to 3f,3f to 20f,12f to 22f,21f to 20f,21f to 3f,12f to 5f,12f to 22f)}
            "link" -> {circle(8f,14f,5f);circle(16f,8f,5f);line(8f,14f,16f,8f)}
            "send" -> path(3f to 3f,22f to 12f,3f to 21f,7f to 12f,3f to 3f,22f to 12f,7f to 12f)
            else -> {drawRoundRect(color,Offset(5*u,2*u),Size(14*u,20*u),CornerRadius(2*u),style=Stroke(1.6f*u));line(9f,8f,15f,8f);line(9f,12f,15f,12f);line(9f,16f,13f,16f)}
        }
    }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Text(title, color = ink, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, color = muted, fontSize = 14.sp, lineHeight = 21.sp)
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun Message(text: String, good: Boolean = false) {
    Text(text, color = if(good) indigo else Color(0xFFAB415A), fontSize = 12.sp,
        lineHeight = 18.sp, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun HomeScreen(state: AppState, c: GstiController, onChat: () -> Unit) {
    Heading("Hola, ${state.profile.name.substringBefore(' ')}", "¿Cómo te sientes hoy?")
    Glass(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Check-in diario", color=ink,fontSize=22.sp,fontWeight=FontWeight.Bold)
                    Text("Tómate un momento para conectar contigo y registrar cómo te sientes hoy.",color=muted,fontSize=13.sp,lineHeight=20.sp,modifier=Modifier.padding(top=8.dp))
                }
                Spacer(Modifier.width(12.dp)); IconTile("form", 54)
            }
            Spacer(Modifier.height(18.dp))
            MainButton("Registrar mi estado de ánimo", !state.hasPendingResponse) {c.openCheckIn(null)}
        }
    }
    if(state.notice!=null) Message(state.notice,true)
    if(state.error!=null) Message(state.error)
    if(state.hasPendingResponse) {Spacer(Modifier.height(12.dp));MainButton("Reintentar envío pendiente", !state.busy,c::retryPending)}
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        QuickCard("Cuestionarios","Explora herramientas para conocerte mejor.","form",Modifier.weight(1f),c::showStudy)
        QuickCard("Chat de apoyo","Un espacio para hablar de cómo te sientes.","chat",Modifier.weight(1f),onChat)
    }
    Spacer(Modifier.height(16.dp))
    Glass(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Cuidar de ti\ntambién es progreso.",color=ink,fontSize=19.sp,lineHeight=25.sp,fontWeight=FontWeight.Bold)
                Text("Pequeños pasos, grandes cambios.",color=muted,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
            }
            SafeIcon("heart",Color(0xFF6BAAEB),Modifier.size(45.dp))
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        SoftAction("Datos de salud",Modifier.weight(1f),c::showHealth)
        SoftAction("Recursos de ayuda",Modifier.weight(1f),c::showResources)
    }
    TextButton(onClick=c::refresh,enabled=!state.busy && !state.preview) {
        Text(if(state.busy) "Actualizando…" else "Actualizar avisos y conexión")
    }
    if(state.prompts.isNotEmpty()) {
        Spacer(Modifier.height(22.dp)); Text("Tus avisos",color=ink,fontSize=18.sp,fontWeight=FontWeight.Bold)
        state.prompts.forEach { prompt ->
            Spacer(Modifier.height(10.dp));Glass(Modifier.fillMaxWidth().clickable(enabled=prompt.isAvailable()){c.openCheckIn(prompt)}) {
                Text("Check-in diario · ${displayTime(prompt.scheduledAt)}",color=ink,fontSize=13.sp)
            }
        }
    }
}

@Composable
private fun QuickCard(title:String,description:String,icon:String,modifier:Modifier,onClick:()->Unit) {
    Glass(modifier.clickable(onClick=onClick)) {
        Column {
            IconTile(icon,42);Spacer(Modifier.height(15.dp))
            Text(title,color=ink,fontSize=16.sp,fontWeight=FontWeight.Bold)
            Text(description,color=muted,fontSize=12.sp,lineHeight=18.sp,modifier=Modifier.padding(top=7.dp).heightIn(min=54.dp))
            Spacer(Modifier.height(14.dp));SafeIcon("arrow",modifier=Modifier.size(22.dp).align(Alignment.End))
        }
    }
}

@Composable
private fun CatalogScreen(state:AppState,onOpen:(String)->Unit,onRefresh:()->Unit) {
    Heading("Cuestionarios","Herramientas breves para conocer cómo te sientes. Responde cuando te lo indique el equipo.")
    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
        Text(if(state.catalogFromServer) "Preguntas del backend" else "Preguntas guardadas en el teléfono",
            color=muted,fontSize=11.sp,modifier=Modifier.weight(1f))
        TextButton(onClick=onRefresh,enabled=!state.busy){Text("Actualizar")}
    }
    if(state.busy) LinearProgressIndicator(modifier=Modifier.fillMaxWidth().padding(bottom=12.dp))
    val descriptions=mapOf("PHQ9" to "Síntomas depresivos", "GAD7" to "Ansiedad", "ULS6" to "Soledad percibida · 6 ítems",
        "CSSRS_SCREEN" to "Cuestionario del estudio")
    val titles=mapOf("PHQ9" to "PHQ-9","GAD7" to "GAD-7","ULS6" to "ULS-6","CSSRS_SCREEN" to "C-SSRS")
    state.questionnaires.forEach { q ->
        Glass(Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    IconTile(if(q.code == "ULS6") "people" else "form",42);Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(titles[q.code] ?: q.code,color=ink,fontSize=17.sp,fontWeight=FontWeight.Bold)
                        Text(descriptions[q.code] ?: q.title,color=muted,fontSize=11.sp,lineHeight=15.sp,modifier=Modifier.padding(top=4.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    SoftAction("Abrir") {if(!state.busy && !state.hasPendingResponse)onOpen(q.code)}
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    SafeIcon("clock",muted,Modifier.size(14.dp));Spacer(Modifier.width(6.dp))
                    Text("${q.items.count { !it.optional }} preguntas" + if(q.items.any { it.optional }) " + impacto opcional" else "",color=muted,fontSize=11.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
    if(state.error!=null) Message(state.error)
    if(state.hasPendingResponse) Message("Confirma el envío pendiente desde el inicio para responder otro cuestionario.")
    if(state.questionnaires.isEmpty() && !state.busy) {
        Glass(Modifier.fillMaxWidth()) {
            Text("Aún no se descargaron las preguntas. Pulsa Actualizar para obtener PHQ-9, GAD-7, ULS-6 y C-SSRS del servidor.",
                color=muted,fontSize=13.sp,lineHeight=20.sp)
        }
    }
}

@Composable
private fun QuestionnairePage(q:Questionnaire?,busy:Boolean,error:String?,onSubmit:(List<Int>,String)->Unit,onResources:()->Unit,onStepChanged:()->Unit) {
    if(q==null)return
    var index by rememberSaveable(q.code,q.version){mutableIntStateOf(0)}
    var answers by rememberSaveable(q.code,q.version){mutableStateOf(List(q.items.size){-1})}
    val startedAt=remember(q.code,q.version){timestampNow()}
    val visible=q.visibleIndices(answers)
    val item=q.items[index]
    val position=visible.indexOf(index)
    val last=position==visible.lastIndex
    val canAdvance=item.optional || item.options.any {it.value==answers[index]}
    Text(q.title,color=ink,fontSize=19.sp,lineHeight=26.sp,fontWeight=FontWeight.Bold)
    Spacer(Modifier.height(18.dp))
    Glass(Modifier.fillMaxWidth()) {
        Column {
            Text("Pregunta ${position+1} de ${visible.size}" + if(item.optional) " · Opcional" else "",color=muted,fontSize=12.sp)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress={(position+1).toFloat()/visible.size},modifier=Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),color=teal,trackColor=Color.White,drawStopIndicator={})
        }
    }
    Spacer(Modifier.height(18.dp))
    Glass(Modifier.fillMaxWidth()) {
        Column {
            IconTile("form",44);Spacer(Modifier.height(16.dp))
            Text(item.text,color=ink,fontSize=21.sp,lineHeight=29.sp,fontWeight=FontWeight.Bold)
            Text(item.timeFrame.ifBlank {q.introduction},color=muted,fontSize=12.sp,lineHeight=18.sp,modifier=Modifier.padding(top=10.dp,bottom=18.dp))
            item.options.forEach { option ->
                val selected=answers[index]==option.value
                Row(Modifier.fillMaxWidth().padding(bottom=9.dp).shadow(if(selected) 5.dp else 2.dp,RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp)).background(if(selected)Color(0xFFD7EAFF) else Color(0xE6F8FCFF))
                    .border(1.dp,if(selected)teal else Color.White,RoundedCornerShape(18.dp))
                    .clickable(enabled=!busy){answers=q.updateAnswer(answers,index,option.value)}.padding(end=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=selected,enabled=!busy,onClick={answers=q.updateAnswer(answers,index,option.value)})
                    Text(option.label,color=ink,fontSize=13.sp,modifier=Modifier.padding(vertical=10.dp))
                }
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.weight(1f)){SoftAction("Anterior",Modifier.fillMaxWidth()){if(position>0 && !busy){index=visible[position-1];onStepChanged()}}}
        Box(Modifier.weight(1f)) {
            if(!last) MainButton("Siguiente",!busy && canAdvance){index=visible[position+1];onStepChanged()}
            else MainButton(if(busy)"Enviando…" else "Finalizar",!busy && q.complete(answers)){onSubmit(answers,startedAt)}
        }
    }
    if(error!=null)Message(error)
    if(q.needsSupport(answers)) {
        Spacer(Modifier.height(12.dp))
        Glass(Modifier.fillMaxWidth()) {
            Column {
                Text("Puedes buscar apoyo ahora",color=ink,fontWeight=FontWeight.Bold)
                Text("No necesitas terminar el cuestionario para acceder a los recursos de ayuda.",color=muted,fontSize=12.sp)
                TextButton(onClick=onResources){Text("Ver recursos de ayuda")}
            }
        }
    } else TextButton(onClick=onResources){Text("Necesito apoyo ahora")}
    Text("Este cuestionario no da un diagnóstico. El equipo revisa las respuestas, pero no necesariamente de inmediato.",color=muted,fontSize=11.sp,lineHeight=16.sp)
    Text(q.attribution,color=muted,fontSize=10.sp,lineHeight=15.sp,modifier=Modifier.padding(top=12.dp))
}

@Composable
private fun ChatPage(onResources:()->Unit) {
    var showInfo by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    if(showInfo) AlertDialog(onDismissRequest={showInfo=false},title={Text("Chat de apoyo")},
        text={Text("Esta conversación es un ejemplo del diseño. La conexión del chatbot se añadirá después. Puedes explorar la pantalla y escribir un borrador sin enviarlo.")},
        confirmButton={TextButton(onClick={showInfo=false}){Text("Entendido")}})
    Heading("Chat de apoyo","Un espacio para hablar sobre cómo te sientes.")
    Text("CONVERSACIÓN DE EJEMPLO",color=muted,fontSize=10.sp,letterSpacing=1.sp,modifier=Modifier.padding(bottom=14.dp))
    Bubble("Hola, estoy aquí para escucharte. ¿Sobre qué te gustaría conversar hoy?",false)
    Bubble("Me he sentido un poco abrumado últimamente.",true)
    Bubble("Gracias por compartirlo. ¿Te gustaría contarme un poco más sobre qué ha estado ocurriendo?",false)
    Bubble("He tenido muchas responsabilidades y me cuesta descansar.",true)
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)){SoftAction("Recursos de ayuda",Modifier.fillMaxWidth(),onResources)}
        Box(Modifier.weight(1f)){SoftAction("Cómo funciona",Modifier.fillMaxWidth()) {showInfo=true}}
    }
    Spacer(Modifier.height(18.dp))
    Glass(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicTextField(value=draft,onValueChange={draft=it},
                modifier=Modifier.weight(1f),textStyle=androidx.compose.ui.text.TextStyle(color=ink,fontSize=13.sp),
                decorationBox={inner->if(draft.isEmpty())Text("Escribe un mensaje",color=muted,fontSize=13.sp);inner()})
            Spacer(Modifier.width(8.dp))
            IconTile("send",36) {showInfo=true}
        }
    }
    Text("Vista de ejemplo. El chat estará disponible al conectar el servicio.",color=muted,fontSize=11.sp,lineHeight=16.sp,modifier=Modifier.padding(top=10.dp))
    Text("No sustituye ayuda profesional.",color=muted,fontSize=11.sp,modifier=Modifier.padding(top=6.dp))
}

@Composable
private fun Bubble(text:String,user:Boolean) {
    Row(Modifier.fillMaxWidth().padding(bottom=13.dp),horizontalArrangement=if(user)Arrangement.End else Arrangement.Start) {
        Box(Modifier.widthIn(max=285.dp).shadow(6.dp,RoundedCornerShape(22.dp),ambientColor=Color(0x227AADD9),spotColor=Color(0x227AADD9))
            .clip(RoundedCornerShape(22.dp)).background(if(user)Brush.verticalGradient(listOf(Color(0xFF57A8F7),Color(0xFF3088E2))) else Brush.verticalGradient(listOf(Color(0xEFFFFFFF),Color(0xC9EAF5FF))))
            .border(1.dp,Color.White.copy(alpha=.8f),RoundedCornerShape(22.dp)).padding(16.dp)) {
            Text(text,color=if(user)Color.White else ink,fontSize=14.sp,lineHeight=21.sp)
        }
    }
}

@Composable
private fun HealthPage(available:Boolean,pending:Boolean,busy:Boolean,error:String?,notice:String?,preview:Boolean,onSync:()->Unit) {
    val launcher=rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()){granted->if(granted.containsAll(HealthConnectCollector.permissions))onSync()}
    Heading("Conecta tus\ndatos de salud","Sincroniza tu información desde Health Connect cuando tú lo decidas.")
    Glass(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconTile("link",60);Spacer(Modifier.width(16.dp))
            Column {Text("Health Connect",color=ink,fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Tu actividad, en un solo lugar.",color=muted,fontSize=12.sp,modifier=Modifier.padding(top=6.dp))}
        }
    }
    Spacer(Modifier.height(18.dp))
    Glass(Modifier.fillMaxWidth()) {
        Column {
            HealthRow("steps","Pasos","Pasos registrados durante el último día.")
            HorizontalDivider(color=Color.White,modifier=Modifier.padding(vertical=14.dp))
            HealthRow("heart","Pulso","Frecuencia cardíaca disponible.")
            HorizontalDivider(color=Color.White,modifier=Modifier.padding(vertical=14.dp))
            HealthRow("moon","Duración del sueño","Duración de la última noche completa.")
        }
    }
    Spacer(Modifier.height(22.dp))
    MainButton(if(busy)"Sincronizando…" else "Autorizar y sincronizar",!busy && (preview || available)) {
        if(!preview){if(pending)onSync() else launcher.launch(HealthConnectCollector.permissions)}
    }
    if(preview)Text("Demostración: la sincronización está desactivada.",color=muted,fontSize=11.sp,modifier=Modifier.padding(top=10.dp))
    if(!preview && !available)Message("Health Connect no está disponible en este dispositivo.")
    if(pending)Message("Hay un lote pendiente de envío.")
    if(error!=null)Message(error)
    if(notice!=null)Message(notice,true)
    Spacer(Modifier.height(18.dp))
    Glass(Modifier.fillMaxWidth()) {
        Column {Text("Sincronización manual",color=ink,fontSize=17.sp,fontWeight=FontWeight.Bold)
            Text("Tú eliges cuándo enviar los datos al estudio. Puedes retirar los permisos desde Health Connect. No hay lectura en segundo plano.",color=muted,fontSize=12.sp,lineHeight=19.sp,modifier=Modifier.padding(top=8.dp))}
    }
}

@Composable
private fun HealthRow(icon:String,title:String,description:String) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        IconTile(icon,46);Spacer(Modifier.width(14.dp))
        Column {Text(title,color=ink,fontSize=16.sp,fontWeight=FontWeight.SemiBold);Text(description,color=muted,fontSize=12.sp,lineHeight=18.sp,modifier=Modifier.padding(top=4.dp))}
    }
}

@Composable
private fun ResourcesPage(back:()->Unit) {
    Heading("Aquí puedes\nencontrar apoyo","Información y recursos para cuando lo necesites.")
    ResourceCard("chat","Contactar apoyo","Habla con una persona de confianza que pueda escucharte y acompañarte.","Orientación · Acompañamiento")
    ResourceCard("help","Ayuda urgente","Si hay peligro inmediato, acude al servicio de emergencias más cercano o pide a alguien de confianza que te acompañe.","Atención inmediata")
    ResourceCard("book","Más recursos","Consulta con el equipo del estudio los servicios de apoyo y las guías disponibles en tu institución.","Información · Servicios de apoyo")
    Glass(Modifier.fillMaxWidth()) {
        Column {Text("Tu bienestar también importa.",color=ink,fontSize=18.sp,fontWeight=FontWeight.Bold)
            Text("Es válido pedir ayuda.",color=muted,fontSize=14.sp,modifier=Modifier.padding(top=6.dp))}
    }
    Spacer(Modifier.height(18.dp));SoftAction("Volver al inicio",Modifier.fillMaxWidth(),back)
}

@Composable
private fun ResourceCard(icon:String,title:String,description:String,tag:String) {
    Glass(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment=Alignment.Top) {
                IconTile(icon,48);Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title,color=ink,fontSize=18.sp,fontWeight=FontWeight.Bold)
                    Text(description,color=muted,fontSize=13.sp,lineHeight=20.sp,modifier=Modifier.padding(top=7.dp))
                }
            }
            Text(tag,color=indigo,fontSize=11.sp,modifier=Modifier.padding(top=15.dp).clip(CircleShape).background(Color.White.copy(alpha=.6f)).padding(horizontal=12.dp,vertical=7.dp))
        }
    }
    Spacer(Modifier.height(15.dp))
}

@Composable
private fun EnrollPage(busy: Boolean, error: String?, register: (String, String) -> Unit) {
    var server by rememberSaveable {
        mutableStateOf(if (BuildConfig.DEBUG) "http://10.0.2.2:8000" else "")
    }
    var code by rememberSaveable { mutableStateOf("") }
    Text("Empecemos con calma.", color = ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Text("Ingresa el código que te entregó el equipo del estudio para vincular este teléfono.",
        color = muted, fontSize = 15.sp, lineHeight = 22.sp)
    Spacer(Modifier.height(27.dp))
    Glass(Modifier.fillMaxWidth()) {
        Column {
            Text("Vincular dispositivo", color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(15.dp))
            OutlinedTextField(code, { code = it.uppercase() }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Código de enrolamiento") }, placeholder = { Text("K7QM-2XRP") },
                singleLine = true, shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(server, { server = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Dirección del servidor") }, singleLine = true,
                shape = RoundedCornerShape(16.dp))
            Text("En el emulador de desarrollo usa 10.0.2.2:8000. En un teléfono real necesitas la dirección HTTPS del servidor.",
                color = muted, fontSize = 11.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(top = 9.dp))
            if (error != null) Message(error)
            Spacer(Modifier.height(18.dp))
            MainButton(if (busy) "Conectando…" else "Vincular teléfono", !busy && code.isNotBlank()) {
                register(server, code)
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Text("Tus datos del estudio se identifican con un seudónimo.", color = muted,
        fontSize = 12.sp)
}

@Composable
private fun CheckInPage(prompt: EmaPrompt?, busy: Boolean, error: String?,
    onSubmit: (List<Int>, String) -> Unit, onResources: () -> Unit) {
    var mood by rememberSaveable(prompt?.id) { mutableIntStateOf(5) }
    var stress by rememberSaveable(prompt?.id) { mutableIntStateOf(5) }
    var loneliness by rememberSaveable(prompt?.id) { mutableIntStateOf(5) }
    var sleep by rememberSaveable(prompt?.id) { mutableIntStateOf(5) }
    val startedAt = remember(prompt?.id) { timestampNow() }
    Text("Check-in diario", color = ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Text("Responde según cómo te sientes ahora. No hay respuestas correctas.",
        color = muted, fontSize = 14.sp, lineHeight = 20.sp)
    Spacer(Modifier.height(23.dp))
    Glass(Modifier.fillMaxWidth()) {
        Column {
            Text("TU BIENESTAR HOY", color = indigo, fontSize = 12.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Scale("¿Cómo está tu ánimo?", "Muy bajo", "Muy bien", mood) { mood = it }
            Scale("¿Cuánto estrés sientes?", "Nada", "Mucho", stress) { stress = it }
            Scale("¿Qué tanta soledad sientes?", "Nada", "Mucha", loneliness) { loneliness = it }
            Scale("¿Cómo fue tu descanso?", "Muy malo", "Muy bueno", sleep) { sleep = it }
            Text("Cada escala va de 0 a 10.", color = muted, fontSize = 12.sp)
            if (error != null) Message(error)
            Spacer(Modifier.height(16.dp))
            MainButton(if (busy) "Enviando…" else "Enviar respuestas", !busy) {
                onSubmit(listOf(mood, stress, loneliness, sleep), startedAt)
            }
        }
    }
    Spacer(Modifier.height(15.dp))
    TextButton(onClick = onResources) { Text("Necesito apoyo ahora", color = indigo) }
}

@Composable
private fun Scale(title: String, low: String, high: String, value: Int, change: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("$value", color = indigo, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
    Slider(value.toFloat(), { change(it.toInt()) }, valueRange = 0f..10f, steps = 9,
        colors = SliderDefaults.colors(thumbColor = indigo, activeTrackColor = teal))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(low, color = muted, fontSize = 11.sp)
        Text(high, color = muted, fontSize = 11.sp)
    }
    Spacer(Modifier.height(23.dp))
}

@Composable
private fun LoginPage(busy: Boolean, error: String?, notice: String?, onLogin: (String, String) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    Spacer(Modifier.height(26.dp))
    IconTile("heart", 66)
    Spacer(Modifier.height(24.dp))
    Heading("Bienvenido a Safe", "Un espacio para conectar contigo y cuidar tu bienestar.")
    Glass(Modifier.fillMaxWidth()) {
        Column {
            Text("Iniciar sesión", color = ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("Ingresa tus datos para continuar.", color = muted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 7.dp, bottom = 20.dp))
            OutlinedTextField(username, { username = it }, enabled = !busy,
                modifier = Modifier.fillMaxWidth(), label = { Text("Usuario") },
                leadingIcon = { SafeIcon("profile") }, singleLine = true, shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(password, { password = it }, enabled = !busy,
                modifier = Modifier.fillMaxWidth(), label = { Text("Contraseña") }, singleLine = true,
                shape = RoundedCornerShape(18.dp),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide(); if (username.isNotBlank() && password.isNotEmpty()) onLogin(username, password)
                }), trailingIcon = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(if (passwordVisible) "Ocultar" else "Mostrar", fontSize = 11.sp)
                    }
                })
            if (error != null) Message(error)
            if (notice != null) Message(notice, true)
            Spacer(Modifier.height(22.dp))
            MainButton(if (busy) "Ingresando…" else "Entrar", !busy && username.isNotBlank() && password.isNotEmpty()) {
                keyboard?.hide(); onLogin(username, password)
            }
        }
    }
    Spacer(Modifier.height(22.dp))
    Text("Tu bienestar, un paso a la vez.", color = muted, fontSize = 13.sp,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ProfileAvatar(profile: LocalProfile, size: Int, onClick: () -> Unit) {
    val photo by produceState<ImageBitmap?>(initialValue = null, profile.photoPath) {
        value = withContext(Dispatchers.IO) {
            profile.photoPath?.let { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() }
        }
    }
    Box(Modifier.size(size.dp).shadow(9.dp, CircleShape, ambientColor = Color(0x337BA9D3), spotColor = Color(0x337BA9D3))
        .clip(CircleShape).background(Brush.linearGradient(listOf(Color.White, Color(0xFFCEE6FF))))
        .border(2.dp, Color.White, CircleShape).clickable(onClick = onClick)
        .semantics { contentDescription = "Foto de perfil" }, contentAlignment = Alignment.Center) {
        if (photo != null) Image(photo!!, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize())
        else Text(profile.name.take(1).uppercase(), color = indigo, fontWeight = FontWeight.Bold, fontSize = (size * .4f).sp)
    }
}

@Composable
private fun ProfileScreen(state: AppState, controller: GstiController) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) controller.setProfilePhoto(uri)
    }
    if (editing) {
        var name by rememberSaveable { mutableStateOf(state.profile.name) }
        var email by rememberSaveable { mutableStateOf(state.profile.email) }
        var bio by rememberSaveable { mutableStateOf(state.profile.bio) }
        AlertDialog(onDismissRequest = { editing = false },
            title = { Text("Editar perfil", color = ink) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it.take(64) }, label = { Text("Nombre") }, singleLine = true)
                    OutlinedTextField(email, { email = it.take(120) }, label = { Text("Correo (opcional)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    OutlinedTextField(bio, { bio = it.take(180) }, label = { Text("Sobre mí (opcional)") }, minLines = 2)
                    Text("Estos datos se guardan en este teléfono.", color = muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { controller.saveProfile(name, email, bio); editing = false },
                    enabled = name.isNotBlank() && !state.busy) { Text("Guardar") }
            }, dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancelar") } })
    }
    Heading("Mi perfil", "Tu espacio en Safe.")
    Glass(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ProfileAvatar(state.profile, 96) { if (!state.busy) picker.launch("image/*") }
            TextButton(onClick = { picker.launch("image/*") }, enabled = !state.busy) {
                Text(if (state.profile.photoPath == null) "Añadir foto" else "Cambiar foto", fontSize = 12.sp)
            }
            Text(state.profile.name, color = ink, fontSize = 23.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center)
            Text("Cuenta local · admin", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            if (state.profile.email.isNotBlank()) Text(state.profile.email, color = muted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 9.dp), textAlign = TextAlign.Center)
            if (state.profile.bio.isNotBlank()) Text(state.profile.bio, color = muted, fontSize = 13.sp,
                lineHeight = 19.sp, modifier = Modifier.padding(top = 10.dp), textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            SoftAction("Editar perfil", Modifier.fillMaxWidth()) { editing = true }
            if (state.profile.photoPath != null) TextButton(onClick = controller::removeProfilePhoto, enabled = !state.busy) {
                Text("Quitar foto", fontSize = 12.sp)
            }
        }
    }
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 14.dp))
    if (state.error != null) Message(state.error)
    if (state.notice != null) Message(state.notice, true)
    Spacer(Modifier.height(18.dp))
    Glass(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile("link", 42); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (state.preview) "Teléfono sin vincular" else "Teléfono vinculado", color = ink, fontWeight = FontWeight.SemiBold)
                Text(if (state.preview) "Vincúlalo para enviar tus respuestas al estudio." else "Participante ${state.participantCode}",
                    color = muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    ProfileAction("Vincular dispositivo", "Conecta tu teléfono con el estudio", "link", controller::showEnrollment)
    ProfileAction("Datos de salud", "Permisos y sincronización manual", "heart", controller::showHealth)
    ProfileAction("Recursos de ayuda", "Información y opciones de apoyo", "help", controller::showResources)
    Text("Tu foto y los datos de este perfil se guardan únicamente en este teléfono.",
        color = muted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(vertical = 10.dp))
    SoftAction("Cerrar sesión", Modifier.fillMaxWidth(), controller::signOut)
}

@Composable
private fun ProfileAction(title: String, subtitle: String, icon: String, action: () -> Unit) {
    Glass(Modifier.fillMaxWidth().clickable(onClick = action)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SafeIcon(icon, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
            SafeIcon("arrow", modifier = Modifier.size(18.dp))
        }
    }
    Spacer(Modifier.height(10.dp))
}
