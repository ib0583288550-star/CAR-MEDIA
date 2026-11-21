package com.ybapps.carmedia

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.ActivityNotFoundException
import android.widget.Toast
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private data class PhoneContact(val name: String, val number: String)
private val Navy = Color(0xFF101923)
private val Panel = Color(0xFF1B2936)
private val Mint = Color(0xFF25C6A5)
private val Muted = Color(0xFF9AAAB8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CarMediaApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarMediaApp() {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var contacts by remember { mutableStateOf(emptyList<PhoneContact>()) }
    var search by remember { mutableStateOf("") }
    var btStatus by remember { mutableStateOf("בדיקת מצב Bluetooth") }
    var hasContactsPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.READ_CONTACTS)
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasContactsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (hasContactsPermission) contacts = loadContacts(context)
    }
    LaunchedEffect(hasContactsPermission) { if (hasContactsPermission) contacts = loadContacts(context) }

    MaterialTheme(colorScheme = darkColorScheme(primary = Mint, background = Navy, surface = Panel)) {
        Scaffold(containerColor = Navy, bottomBar = {
            NavigationBar(containerColor = Panel) {
                NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Default.DirectionsCar, null) }, label = { Text("ראשי") })
                NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Default.Contacts, null) }, label = { Text("אנשי קשר") })
                NavigationBarItem(tab == 2, { tab = 2 }, icon = { Icon(Icons.Default.Bluetooth, null) }, label = { Text("Bluetooth") })
            }
        }) { inset ->
            Column(Modifier.fillMaxSize().padding(inset).padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("CAR MEDIA", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("מרכז השליטה שלך", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.size(48.dp).background(Panel, CircleShape), Alignment.Center) {
                        Icon(Icons.Default.DirectionsCar, null, tint = Mint, modifier = Modifier.size(27.dp))
                    }
                }
                when (tab) {
                    0 -> HomeScreen(contacts.size, onContacts = { tab = 1 }, onBluetooth = { tab = 2 })
                    1 -> {
                        Text("אנשי קשר", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = { Text("חיפוש איש קשר") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
                        Spacer(Modifier.height(10.dp))
                        if (!hasContactsPermission) Button(onClick = { permissionRequest.launch(permissions) }, Modifier.fillMaxWidth()) { Text("אפשר גישה לאנשי קשר") }
                        else if (contacts.isEmpty()) InfoPanel("לא נמצאו אנשי קשר", "בדוק שיש אנשי קשר שמורים במכשיר")
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(contacts.filter { it.name.contains(search, true) || it.number.contains(search) }) { contact ->
                                ContactRow(contact) { dial(context, contact.number) }
                            }
                        }
                    }
                    else -> BluetoothScreen(btStatus, onCheck = {
                        btStatus = try {
                            val adapter = BluetoothAdapter.getDefaultAdapter()
                            when {
                                adapter == null -> "המכשיר אינו כולל Bluetooth"
                                !adapter.isEnabled -> "Bluetooth כבוי — יש להפעיל אותו בהגדרות"
                                else -> "Bluetooth פעיל. בצע צימוד דרך הגדרות Android"
                            }
                        } catch (_: SecurityException) { "נדרשת הרשאת Bluetooth" }
                    }, onSettings = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) })
                }
                Spacer(Modifier.height(12.dp))
                Text("yb apps", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
            }
        }
    }
}

@Composable
private fun HomeScreen(count: Int, onContacts: () -> Unit, onBluetooth: () -> Unit) {
    val context = LocalContext.current
    var number by remember { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("חיבור לטלפון", color = Muted)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(Mint, CircleShape))
                Spacer(Modifier.width(9.dp))
                Text("מוכן להגדרה", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onBluetooth, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Navy)) {
                    Icon(Icons.Default.Bluetooth, null); Spacer(Modifier.width(5.dp)); Text("חיבור")
                }
                OutlinedButton(onClick = onContacts, Modifier.weight(1f)) {
                    Icon(Icons.Default.Contacts, null); Spacer(Modifier.width(5.dp)); Text("$count אנשי קשר")
                }
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    Text("חיוג מהיר", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(number, { number = it.filter { c -> c.isDigit() || c in "+*#" } }, Modifier.fillMaxWidth(), placeholder = { Text("הזן מספר טלפון") }, leadingIcon = { Icon(Icons.Default.Phone, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
    Spacer(Modifier.height(12.dp))
    Button(onClick = { if (number.isNotBlank()) dial(context, number) }, enabled = number.isNotBlank(), modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Navy), shape = RoundedCornerShape(16.dp)) {
        Icon(Icons.Default.Call, null); Spacer(Modifier.width(8.dp)); Text("פתח חיוג", fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(18.dp))
    InfoPanel("שליטה ברכב", "החיוג נפתח דרך אפליקציית הטלפון של Android. דיבורית מלאה דורשת תמיכה של הנגן בפרופיל Bluetooth HFP Client.")
}

@Composable
private fun ContactRow(contact: PhoneContact, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Color(0xFF294253), CircleShape), Alignment.Center) {
                Text(contact.name.take(1), color = Mint, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(contact.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(contact.number, color = Muted, fontSize = 13.sp)
            }
            Icon(Icons.Default.Call, null, tint = Mint)
        }
    }
}

@Composable
private fun BluetoothScreen(status: String, onCheck: () -> Unit, onSettings: () -> Unit) {
    Text("Bluetooth", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(14.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Bluetooth, null, tint = Mint, modifier = Modifier.size(54.dp))
            Spacer(Modifier.height(12.dp))
            Text("קישור לטלפון", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("$status\n\nיש לבצע צימוד דרך הגדרות Bluetooth של Android. לא כל נגן תומך בפרופיל דיבורית HFP Client.", color = Muted, lineHeight = 21.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCheck, Modifier.fillMaxWidth()) { Text("בדוק מצב Bluetooth") }
            OutlinedButton(onClick = onSettings, Modifier.fillMaxWidth()) { Text("פתח הגדרות Bluetooth") }
        }
    }
}

@Composable
private fun InfoPanel(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(body, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

private fun loadContacts(context: android.content.Context): List<PhoneContact> {
    val result = mutableListOf<PhoneContact>()
    return try {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC"
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) result.add(PhoneContact(cursor.getString(nameCol) ?: "ללא שם", cursor.getString(numberCol) ?: ""))
        }
        result.distinctBy { it.number }
    } catch (_: Exception) { emptyList() }
}

private fun dial(context: android.content.Context, number: String) {
    val cleanNumber = number.trim()
    if (cleanNumber.isBlank()) {
        Toast.makeText(context, "נא להזין מספר טלפון", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.fromParts("tel", cleanNumber, null)
    }
    try {
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "לא נמצאה במכשיר אפליקציית חיוג", Toast.LENGTH_LONG).show()
        }
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "לא ניתן לפתוח את החייגן במכשיר הזה", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "אין הרשאה לפתוח את החייגן", Toast.LENGTH_LONG).show()
    } catch (_: Exception) {
        Toast.makeText(context, "אירעה שגיאה בפתיחת החייגן", Toast.LENGTH_LONG).show()
    }
}
