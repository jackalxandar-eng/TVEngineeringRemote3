package com.tvengineer.pro.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvengineer.pro.controller.TvController
import com.tvengineer.pro.data.ProfileStore
import com.tvengineer.pro.discovery.SsdpDiscovery
import com.tvengineer.pro.model.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private enum class Screen { MODE, NORMAL, SERVICE, DEVICES, ENGINEER, DIAGNOSTICS, LOGS, SETTINGS }

@Composable
fun TVEngineerApp() {
    val context=LocalContext.current
    val store=remember{ProfileStore(context)}
    val controller=remember{TvController(context)}
    var profiles by remember{ mutableStateOf(store.load()) }
    var selectedId by remember{ mutableStateOf(profiles.firstOrNull()?.id.orEmpty()) }
    val selected=profiles.firstOrNull{it.id==selectedId} ?: profiles.first()
    var screen by remember{ mutableStateOf(Screen.MODE) }
    var engineerUnlocked by remember{ mutableStateOf(false) }
    val logs=remember{ mutableStateListOf<LogEntry>() }
    val scope=rememberCoroutineScope()
    var busy by remember{ mutableStateOf(false) }
    var confirmDanger by remember{ mutableStateOf<RemoteCommand?>(null) }
    var showUnlock by remember{ mutableStateOf(false) }

    fun log(msg:String,level:String="INFO"){logs.add(0,LogEntry(level=level,message=msg))}
    fun saveProfiles(newItems:MutableList<DeviceProfile>){profiles=newItems;store.save(newItems)}

    suspend fun dispatch(cmd:RemoteCommand){
        busy=true
        val res=controller.send(selected,cmd.payload)
        res.onSuccess{log("${cmd.key}: $it","TX")}.onFailure{log("${cmd.key}: ${it.message}","ERROR")}
        busy=false
    }
    fun send(cmd:RemoteCommand?) {
        if(cmd==null){log("Command is not mapped for ${selected.name}","WARN");return}
        if(cmd.risk!=CommandRisk.NORMAL && !engineerUnlocked){showUnlock=true;return}
        if(cmd.risk==CommandRisk.DANGEROUS){confirmDanger=cmd;return}
        scope.launch{dispatch(cmd)}
    }

    Surface(Modifier.fillMaxSize(), color=Bg) {
        Column {
            TopBar(selected,controller.irStatus(),busy,
                onDevices={screen=Screen.DEVICES},onMode={screen=Screen.MODE})
            Box(Modifier.weight(1f)) {
                when(screen){
                    Screen.MODE -> ModeScreen(onNormal={screen=Screen.NORMAL},onService={screen=Screen.SERVICE})
                    Screen.NORMAL -> NormalRemote(selected,{key->send(selected.normal[key])},{screen=Screen.DEVICES},{screen=Screen.ENGINEER})
                    Screen.SERVICE -> ServiceRemote(selected,engineerUnlocked,{showUnlock=true},{key->send(selected.service[key])})
                    Screen.DEVICES -> DevicesScreen(profiles,selectedId,
                        onSelect={selectedId=it;screen=Screen.NORMAL},
                        onUpdate={p->
                            val m=profiles.toMutableList()
                            val i=m.indexOfFirst{it.id==p.id}
                            if(i>=0)m[i]=p else m.add(p)
                            saveProfiles(m);selectedId=p.id
                        },
                        onDelete={id->
                            val m=profiles.filterNot{it.id==id}.toMutableList()
                            if(m.isNotEmpty()){saveProfiles(m);selectedId=m.first().id}
                        })
                    Screen.ENGINEER -> EngineerScreen(selected,engineerUnlocked,{showUnlock=true},
                        onAddCommand={isService,c->
                            val p=if(isService) selected.copy(service=selected.service+(c.key to c))
                            else selected.copy(normal=selected.normal+(c.key to c))
                            val m=profiles.toMutableList();m[m.indexOfFirst{it.id==p.id}]=p;saveProfiles(m)
                            log("Saved ${c.key} in ${if(isService)"service" else "normal"} profile")
                        },
                        onDiagnostics={screen=Screen.DIAGNOSTICS},onLogs={screen=Screen.LOGS})
                    Screen.DIAGNOSTICS -> DiagnosticsScreen(selected,controller,context,{log(it.first,it.second)})
                    Screen.LOGS -> LogsScreen(logs)
                    Screen.SETTINGS -> SettingsScreen()
                }
            }
            BottomBar(screen,
                onRemote={screen=Screen.NORMAL},onService={screen=Screen.SERVICE},
                onEngineer={screen=Screen.ENGINEER},onLogs={screen=Screen.LOGS})
        }
    }

    if(showUnlock) {
        UnlockDialog(onDismiss={showUnlock=false},onUnlock={
            engineerUnlocked=true;showUnlock=false;log("Engineer mode unlocked","SECURITY")
        })
    }
    confirmDanger?.let { c ->
        AlertDialog(onDismissRequest={confirmDanger=null},
            title={Text("Sensitive service command")},
            text={Text("This command is marked DANGEROUS and can alter factory/service settings. Send it only to equipment you own or are authorized to service.")},
            confirmButton={TextButton(onClick={confirmDanger=null;scope.launch{dispatch(c)}}){Text("SEND",color=Red)}},
            dismissButton={TextButton(onClick={confirmDanger=null}){Text("Cancel")}})
    }
}

@Composable
private fun TopBar(p:DeviceProfile,ir:String,busy:Boolean,onDevices:()->Unit,onMode:()->Unit){
    Column(Modifier.fillMaxWidth().background(Color(0xFF090D14)).padding(14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TV ENGINEERING REMOTE",fontSize=18.sp,fontWeight=FontWeight.Black,letterSpacing=1.sp,color=Cyan)
                Text("${p.name} • ${p.transport.name.replace('_',' ')}",fontSize=12.sp,color=Muted)
            }
            StatusPill(if(busy)"WORKING" else "READY",if(busy)Amber else Green)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TinyButton("MODE",onMode); TinyButton("DEVICES",onDevices)
            Text(ir,fontSize=10.sp,color=Muted,modifier=Modifier.weight(1f).align(Alignment.CenterVertically),textAlign=TextAlign.End)
        }
    }
}

@Composable
private fun ModeScreen(onNormal:()->Unit,onService:()->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement=Arrangement.Center) {
        Text("SELECT CONTROL MODE",fontSize=13.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            ModeCard("⌁","NORMAL","Full Remote\nWiFi + IR",onNormal,Modifier.weight(1f))
            ModeCard("◉","SERVICE","Service Remote\nInfrared + Network",onService,Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        EngineerNotice()
    }
}

@Composable
private fun ModeCard(icon:String,title:String,subtitle:String,onClick:()->Unit,modifier:Modifier=Modifier){
    Box(modifier.height(250.dp).shadow(20.dp,RoundedCornerShape(28.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF1A202D),Color(0xFF0D1119))),RoundedCornerShape(28.dp))
        .border(1.dp,Cyan.copy(alpha=.25f),RoundedCornerShape(28.dp)).clickable{onClick()}.padding(20.dp)) {
        Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Text(icon,fontSize=52.sp,color=Cyan);Spacer(Modifier.height(20.dp))
            Text(title,fontSize=27.sp,color=Cyan,fontWeight=FontWeight.Black,letterSpacing=2.sp)
            Spacer(Modifier.height(18.dp))
            Text(subtitle,fontSize=17.sp,color=Muted,textAlign=TextAlign.Center,lineHeight=26.sp)
        }
    }
}

@Composable
private fun NormalRemote(p:DeviceProfile,onKey:(String)->Unit,onDevices:()->Unit,onEngineer:()->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=14.dp,vertical=12.dp)) {
        Section("QUICK")
        KeyGrid(listOf("POWER","HOME","INPUT","SETTINGS"),onKey,4)
        Section("APPS")
        KeyGrid(listOf("NETFLIX","DISNEY","PRIME","WEB"),onKey,4)
        Section("TV INFO & GUIDE")
        KeyGrid(listOf("INFO","GUIDE","CH_LIST","SOURCE"),onKey,4)
        Section("NAVIGATION")
        DPad(onKey)
        KeyGrid(listOf("VOL_DOWN","VOL_UP","CH_DOWN","CH_UP"),onKey,4)
        KeyGrid(listOf("BACK","EXIT","LIVE","PRE_CH","MUTE"),onKey,5)
        Section("MEDIA")
        KeyGrid(listOf("REW","PLAY","PAUSE","STOP","FWD"),onKey,5)
        Section("NUMPAD")
        (1..9).chunked(3).forEach { row->KeyGrid(row.map{it.toString()},onKey,3) }
        KeyGrid(listOf("0"),onKey,1)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            ProButton("DEVICE MANAGER",Modifier.weight(1f),onDevices)
            ProButton("ENGINEERING LAB",Modifier.weight(1f),onEngineer)
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ServiceRemote(p:DeviceProfile,unlocked:Boolean,onUnlock:()->Unit,onKey:(String)->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            Text("SERVICE REMOTE",fontSize=26.sp,fontWeight=FontWeight.Black,color=Cyan,modifier=Modifier.weight(1f))
            StatusPill(if(unlocked)"UNLOCKED" else "LOCKED",if(unlocked)Green else Red)
        }
        Spacer(Modifier.height(8.dp))
        Text("Service keys are profile-specific. Unsupported keys are intentionally not fabricated.",color=Muted,fontSize=12.sp)
        if(!unlocked){Spacer(Modifier.height(12.dp));ProButton("UNLOCK ENGINEER MODE",Modifier.fillMaxWidth(),onUnlock,accent=Red)}
        Section("SERVICE CONTROLS")
        KeyGrid(listOf("POWER","MUTE","HOME","SSM","IN_START","SOUND","EXIT","ADJUST","PWR_ONLY","IN_STOP","GUIDE","PSM"),onKey,3)
        Section("NAVIGATION");DPad(onKey)
        KeyGrid(listOf("VOL_DOWN","VOL_UP","CH_DOWN","CH_UP"),onKey,4)
        Section("NUMPAD")
        (1..9).chunked(3).forEach{KeyGrid(it.map(Int::toString),onKey,3)}
        KeyGrid(listOf("0"),onKey,1)
        Section("ENGINEERING SAFETY")
        EngineerNotice()
        Spacer(Modifier.height(35.dp))
    }
}

@Composable
private fun EngineerScreen(
    profile:DeviceProfile, unlocked:Boolean,onUnlock:()->Unit,
    onAddCommand:(Boolean,RemoteCommand)->Unit,onDiagnostics:()->Unit,onLogs:()->Unit
){
    var key by remember{mutableStateOf("")}
    var label by remember{mutableStateOf("")}
    var payload by remember{mutableStateOf("")}
    var service by remember{mutableStateOf(false)}
    var risk by remember{mutableStateOf(CommandRisk.NORMAL)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)){
        Text("ENGINEERING LAB",fontSize=26.sp,fontWeight=FontWeight.Black,color=Cyan)
        Spacer(Modifier.height(6.dp))
        Text("RAW • Pronto • NEC • Samsung32 • Sony SIRC • Network payloads",color=Muted,fontSize=12.sp)
        if(!unlocked){Spacer(Modifier.height(14.dp));ProButton("UNLOCK ENGINEER MODE",Modifier.fillMaxWidth(),onUnlock,accent=Red);return@Column}
        Section("COMMAND BUILDER")
        ProField("Key / button name",key){key=it.uppercase().replace(" ","_")}
        ProField("Display label",label){label=it}
        ProField("Payload",payload){payload=it}
        Text("IR examples: NEC:0x07:0x02  •  SAMSUNG32:0xE0E040BF  •  RAW:38000:9000,4500,...  •  PRONTO:0000 ...",
            color=Muted,fontSize=10.sp,lineHeight=15.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment=Alignment.CenterVertically){
            Checkbox(service,{service=it});Text("Service command",color=Text)
            Spacer(Modifier.width(14.dp))
            TextButton(onClick={risk=when(risk){CommandRisk.NORMAL->CommandRisk.ENGINEER;CommandRisk.ENGINEER->CommandRisk.DANGEROUS;CommandRisk.DANGEROUS->CommandRisk.NORMAL}}){
                Text("Risk: ${risk.name}",color=when(risk){CommandRisk.NORMAL->Green;CommandRisk.ENGINEER->Amber;CommandRisk.DANGEROUS->Red})
            }
        }
        ProButton("SAVE COMMAND",Modifier.fillMaxWidth(),{
            if(key.isNotBlank() && payload.isNotBlank()){
                onAddCommand(service,RemoteCommand(key,label.ifBlank{key},payload,risk))
                key="";label="";payload=""
            }
        })
        Section("TOOLS")
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            ProButton("DIAGNOSTICS",Modifier.weight(1f),onDiagnostics)
            ProButton("EVENT LOG",Modifier.weight(1f),onLogs)
        }
        Spacer(Modifier.height(12.dp))
        InfoPanel("Current profile","${profile.name}\n${profile.brand} ${profile.model}\nNormal: ${profile.normal.size} commands\nService: ${profile.service.size} commands")
        Section("SERVICE VAULT")
        Text("Hidden/service functions are only enabled when a verified model profile supplies a command. This prevents applying another model's factory sequence to the wrong panel.",color=Muted,fontSize=12.sp)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun DevicesScreen(
    profiles:List<DeviceProfile>,selectedId:String,onSelect:(String)->Unit,
    onUpdate:(DeviceProfile)->Unit,onDelete:(String)->Unit
){
    var editing by remember{mutableStateOf<DeviceProfile?>(null)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)){
        Text("DEVICE MANAGER",fontSize=26.sp,fontWeight=FontWeight.Black,color=Cyan)
        Text("IR + Wi-Fi/IP profiles",color=Muted,fontSize=12.sp)
        profiles.forEach { p->
            Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp),
                border=BorderStroke(1.dp,if(p.id==selectedId)Cyan.copy(.55f) else Color.White.copy(.06f)),
                modifier=Modifier.fillMaxWidth().padding(top=10.dp).clickable{onSelect(p.id)}) {
                Column(Modifier.padding(14.dp)){
                    Row{
                        Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.Bold);Text("${p.brand} • ${p.model}",color=Muted,fontSize=12.sp)}
                        StatusPill(p.transport.name.replace('_',' '),Cyan)
                    }
                    Text("Host: ${p.host.ifBlank{"not set"}}   Port: ${p.port}",color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=7.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=8.dp)){
                        TinyButton("EDIT"){editing=p}
                        if(p.id !in setOf("generic-ir","lg-webos","samsung-tizen","roku","philips","sony")) TinyButton("DELETE"){onDelete(p.id)}
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ProButton("ADD CUSTOM DEVICE",Modifier.fillMaxWidth(),{
            editing=DeviceProfile(UUID.randomUUID().toString(),"New TV","Generic","Unknown",TransportType.IR)
        })
        Spacer(Modifier.height(30.dp))
    }
    editing?.let{p->ProfileEditor(p,onDismiss={editing=null},onSave={onUpdate(it);editing=null})}
}

@Composable
private fun ProfileEditor(p:DeviceProfile,onDismiss:()->Unit,onSave:(DeviceProfile)->Unit){
    var name by remember{mutableStateOf(p.name)}
    var brand by remember{mutableStateOf(p.brand)}
    var model by remember{mutableStateOf(p.model)}
    var host by remember{mutableStateOf(p.host)}
    var port by remember{mutableStateOf(if(p.port==0)"" else p.port.toString())}
    var token by remember{mutableStateOf(p.token)}
    var clientKey by remember{mutableStateOf(p.clientKey)}
    var psk by remember{mutableStateOf(p.psk)}
    var type by remember{mutableStateOf(p.transport)}
    AlertDialog(onDismissRequest=onDismiss,
        title={Text("Device profile")},
        text={
            Column(Modifier.verticalScroll(rememberScrollState())){
                ProField("Name",name){name=it};ProField("Brand",brand){brand=it};ProField("Model",model){model=it}
                ProField("IP / Host",host){host=it};ProField("Port",port){port=it.filter(Char::isDigit)}
                Text("Transport: ${type.name}",color=Cyan,fontSize=12.sp)
                FlowTransport(type){type=it}
                if(type==TransportType.SAMSUNG_TIZEN) ProField("Samsung token",token){token=it}
                if(type==TransportType.LG_WEBOS) ProField("LG client-key",clientKey){clientKey=it}
                if(type==TransportType.SONY_IRCC) ProField("Sony PSK",psk){psk=it}
            }
        },
        confirmButton={TextButton(onClick={
            onSave(p.copy(name=name,brand=brand,model=model,host=host,port=port.toIntOrNull()?:p.port,
                token=token,clientKey=clientKey,psk=psk,transport=type))
        }){Text("SAVE",color=Cyan)}},
        dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable
private fun FlowTransport(current:TransportType,onSelect:(TransportType)->Unit){
    Column{
        TransportType.entries.chunked(2).forEach{row->
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                row.forEach{t->TextButton(onClick={onSelect(t)},modifier=Modifier.weight(1f)){
                    Text(t.name.replace('_',' '),fontSize=9.sp,color=if(t==current)Cyan else Muted)
                }}
                if(row.size==1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DiagnosticsScreen(profile:DeviceProfile,controller:TvController,context:Context,onLog:(Pair<String,String>)->Unit){
    val scope=rememberCoroutineScope()
    var probe by remember{mutableStateOf("Not tested")}
    var discovered by remember{mutableStateOf<List<DiscoveryResult>>(emptyList())}
    var scanning by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)){
        Text("DIAGNOSTICS",fontSize=26.sp,fontWeight=FontWeight.Black,color=Cyan)
        Section("HARDWARE")
        InfoPanel("Consumer IR",controller.irStatus())
        InfoPanel("Selected transport",profile.transport.name.replace('_',' '))
        InfoPanel("Target","${profile.host.ifBlank{"not configured"}}:${profile.port}")
        ProButton("PROBE SELECTED DEVICE",Modifier.fillMaxWidth(),{
            scope.launch{
                probe="Testing..."
                controller.probe(profile).onSuccess{probe=it;onLog("Probe OK" to "DIAG")}
                    .onFailure{probe=it.message?:"Failed";onLog("Probe failed: ${it.message}" to "ERROR")}
            }
        })
        InfoPanel("Probe result",probe)
        Section("LAN DISCOVERY")
        ProButton(if(scanning)"SCANNING..." else "SSDP DISCOVERY",Modifier.fillMaxWidth(),{
            scope.launch{scanning=true;discovered=SsdpDiscovery(context).scan();scanning=false}
        })
        discovered.forEach{d->InfoPanel("${d.hint} • ${d.host}",d.server.ifBlank{d.location})}
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun LogsScreen(logs:List<LogEntry>){
    val fmt=remember{SimpleDateFormat("HH:mm:ss",Locale.US)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)){
        Text("EVENT LOG",fontSize=26.sp,fontWeight=FontWeight.Black,color=Cyan)
        logs.forEach { l->
            Row(Modifier.fillMaxWidth().padding(vertical=5.dp)){
                Text(fmt.format(Date(l.time)),fontSize=10.sp,color=Muted,modifier=Modifier.width(70.dp))
                Text(l.level,fontSize=10.sp,fontWeight=FontWeight.Bold,
                    color=when(l.level){"ERROR"->Red;"TX"->Green;"WARN"->Amber;else->Cyan},modifier=Modifier.width(65.dp))
                Text(l.message,fontSize=11.sp,color=Text,modifier=Modifier.weight(1f))
            }
            HorizontalDivider(color=Color.White.copy(.05f))
        }
        if(logs.isEmpty()) Text("No events yet.",color=Muted)
    }
}

@Composable
private fun SettingsScreen(){Column(Modifier.padding(20.dp)){Text("SETTINGS",fontSize=26.sp,fontWeight=FontWeight.Black,color=Cyan)}}

@Composable
private fun DPad(onKey:(String)->Unit){
    Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth().padding(vertical=10.dp)){
        RoundKey("▲"){onKey("UP")}
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)){
            RoundKey("◀",accent=Amber){onKey("LEFT")}
            RoundKey("OK",accent=Cyan){onKey("OK")}
            RoundKey("▶",accent=Amber){onKey("RIGHT")}
        }
        RoundKey("▼"){onKey("DOWN")}
    }
}

@Composable
private fun RoundKey(text:String,accent:Color=Cyan,onClick:()->Unit){
    Box(Modifier.size(if(text=="OK")78.dp else 88.dp).padding(5.dp)
        .shadow(14.dp,CircleShape).background(Brush.verticalGradient(listOf(Color(0xFF252B37),Color(0xFF11151C))),CircleShape)
        .border(if(text=="OK")2.dp else 1.dp,accent.copy(if(text=="OK") .65f else .10f),CircleShape)
        .clickable{onClick()},contentAlignment=Alignment.Center){
        Text(text,color=accent,fontWeight=FontWeight.Black,fontSize=if(text=="OK")24.sp else 28.sp)
    }
}

@Composable
private fun KeyGrid(keys:List<String>,onKey:(String)->Unit,columns:Int){
    keys.chunked(columns).forEach { row->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            row.forEach { key->
                RemoteKey(key,Modifier.weight(1f)){onKey(key)}
            }
            repeat(columns-row.size){Spacer(Modifier.weight(1f))}
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RemoteKey(key:String,modifier:Modifier=Modifier,onClick:()->Unit){
    val label=when(key){
        "VOL_DOWN"->"VOL −";"VOL_UP"->"VOL +";"CH_DOWN"->"CH −";"CH_UP"->"CH +";
        "PRE_CH"->"PRE-CH";"IN_START"->"IN START";"PWR_ONLY"->"PWR ONLY";"IN_STOP"->"IN STOP";
        else->key.replace('_',' ')
    }
    val c=when(key){"POWER"->Red;"LEFT","RIGHT"->Amber;else->Text}
    Box(modifier.height(62.dp).shadow(9.dp,RoundedCornerShape(15.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF252B36),Color(0xFF12161D))),RoundedCornerShape(15.dp))
        .border(1.dp,Color.White.copy(.06f),RoundedCornerShape(15.dp)).clickable{onClick()},
        contentAlignment=Alignment.Center){
        Text(label,color=c,fontSize=if(label.length>9)11.sp else 15.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
    }
}

@Composable
private fun Section(title:String){
    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=18.dp,bottom=10.dp)){
        Box(Modifier.width(4.dp).height(24.dp).background(Cyan,RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(title,color=Cyan,fontSize=15.sp,fontWeight=FontWeight.Black,letterSpacing=1.5.sp)
    }
}

@Composable
private fun InfoPanel(title:String,value:String){
    Column(Modifier.fillMaxWidth().padding(vertical=6.dp)
        .background(Panel,RoundedCornerShape(14.dp)).border(1.dp,Color.White.copy(.05f),RoundedCornerShape(14.dp)).padding(12.dp)){
        Text(title,color=Cyan,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
        Text(value,color=Text,fontSize=12.sp,modifier=Modifier.padding(top=4.dp))
    }
}

@Composable
private fun EngineerNotice(){
    InfoPanel("ENGINEERING MODE",
        "Service/factory commands differ by brand, chassis and exact model. This app never invents hidden codes. Import a verified service profile or map commands manually. Dangerous commands require explicit confirmation.")
}

@Composable
private fun ProButton(text:String,modifier:Modifier=Modifier,onClick:()->Unit,accent:Color=Cyan){
    Button(onClick=onClick,modifier=modifier.height(50.dp),shape=RoundedCornerShape(13.dp),
        colors=ButtonDefaults.buttonColors(containerColor=accent.copy(.15f),contentColor=accent),
        border=BorderStroke(1.dp,accent.copy(.35f))){
        Text(text,fontWeight=FontWeight.Black,fontSize=12.sp)
    }
}

@Composable
private fun TinyButton(text:String,onClick:()->Unit){
    OutlinedButton(onClick=onClick,contentPadding=PaddingValues(horizontal=10.dp,vertical=3.dp),
        modifier=Modifier.height(30.dp),border=BorderStroke(1.dp,Cyan.copy(.25f))){
        Text(text,fontSize=9.sp,color=Cyan)
    }
}

@Composable
private fun StatusPill(text:String,color:Color){
    Box(Modifier.background(color.copy(.12f),RoundedCornerShape(50)).border(1.dp,color.copy(.3f),RoundedCornerShape(50)).padding(horizontal=9.dp,vertical=5.dp)){
        Text(text,fontSize=9.sp,fontWeight=FontWeight.Bold,color=color)
    }
}

@Composable
private fun ProField(label:String,value:String,onChange:(String)->Unit){
    OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},singleLine=true,
        modifier=Modifier.fillMaxWidth().padding(vertical=4.dp),
        colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Cyan,unfocusedBorderColor=Color.White.copy(.12f),
            focusedLabelColor=Cyan,unfocusedLabelColor=Muted,focusedTextColor=Text,unfocusedTextColor=Text))
}

@Composable
private fun BottomBar(screen:Screen,onRemote:()->Unit,onService:()->Unit,onEngineer:()->Unit,onLogs:()->Unit){
    Row(Modifier.fillMaxWidth().background(Color(0xFF080B11)).padding(8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
        listOf("REMOTE" to onRemote,"SERVICE" to onService,"ENGINEER" to onEngineer,"LOGS" to onLogs).forEach{(name,cb)->
            val active=when(name){"REMOTE"->screen==Screen.NORMAL;"SERVICE"->screen==Screen.SERVICE;"ENGINEER"->screen==Screen.ENGINEER;"LOGS"->screen==Screen.LOGS;else->false}
            TextButton(onClick=cb,modifier=Modifier.weight(1f)){
                Text(name,fontSize=9.sp,fontWeight=FontWeight.Bold,color=if(active)Cyan else Muted)
            }
        }
    }
}

@Composable
private fun UnlockDialog(onDismiss:()->Unit,onUnlock:()->Unit){
    var phrase by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Engineer Mode")},
        text={Column{
            Text("Type SERVICE to unlock advanced controls for this session. Service commands can alter factory settings.")
            Spacer(Modifier.height(8.dp));ProField("Confirmation",phrase){phrase=it.uppercase()}
        }},
        confirmButton={TextButton(enabled=phrase=="SERVICE",onClick=onUnlock){Text("UNLOCK",color=if(phrase=="SERVICE")Cyan else Muted)}},
        dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
