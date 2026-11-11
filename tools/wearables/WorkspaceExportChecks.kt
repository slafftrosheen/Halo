package checks
import com.halo.app.data.workspace.*
import com.halo.app.data.notebook.WorkspaceObjectId
import android.net.Uri
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest
fun main() = runBlocking {
 val root = java.nio.file.Files.createTempDirectory("export-check").toFile()
 try {
  val original = File(root,"source"); original.outputStream().use { out -> repeat(384) { out.write(ByteArray(65536) { (it % 251).toByte() }) } }
  fun hash(file:File):String { val d=MessageDigest.getInstance("SHA-256");file.inputStream().use { input -> val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;d.update(b,0,n)} };return d.digest().joinToString(""){"%02x".format(it)} }
  val sourceUri=Uri.parse("content://test/source");val targetUri=Uri.parse("content://test/target")
  val file=WorkspaceFile(WorkspaceObjectId("video"),sourceUri,"video.mp4","video/mp4",original.length(),FilePermissionMode.OWNED_COPY,ContentOwnership.HALO_OWNED,WorkspaceFileAvailability.OWNED_LOCAL,hash(original))
  val version=WorkspaceFileVersion("video",file.sha256!!);val target=File(root,"target");val stage=File(root,"snapshot")
  var allowed=true;var writes=0;var corrupt=false;var unavailable=false
  fun exporter(limit:Long=256L*1024*1024)=WorkspaceExternalExport({file},{uri->if(uri==sourceUri) original.inputStream() else { check(!unavailable);target.inputStream() }},{writes++;if(corrupt) object:java.io.OutputStream(){override fun write(b:Int){};override fun close(){target.writeBytes(byteArrayOf(1))}} else target.outputStream()},{allowed},{stage},limit)
  check(exporter().write(version,targetUri).outcome==ExternalExportOutcome.VERIFIED && hash(target)==file.sha256 && !stage.exists());println("PASS 24 MiB stream export and snapshot cleanup")
  val count=writes;check(exporter().verify(version,targetUri).outcome==ExternalExportOutcome.VERIFIED&&writes==count);println("PASS verify performs no second write")
  check(exporter(1024).write(version,targetUri).outcome==ExternalExportOutcome.SOURCE_UNAVAILABLE&&writes==count&&!stage.exists());println("PASS limit failure preserves destination")
  corrupt=true;check(exporter().write(version,targetUri).outcome==ExternalExportOutcome.WRITE_FAILED);println("PASS corrupt destination cannot verify")
  corrupt=false;unavailable=true;check(exporter().write(version,targetUri).outcome==ExternalExportOutcome.UNKNOWN);println("PASS unreadable destination stays unknown")
  unavailable=false;allowed=false;val before=writes;check(exporter().write(version,targetUri).outcome!=ExternalExportOutcome.VERIFIED&&writes==before);println("PASS revoked access cannot write")
  println("6 exporter checks passed; actual exporter and Android URI API, isolated Workspace metadata boundary (not Room/SAF)")
 } finally {root.deleteRecursively()}
}
