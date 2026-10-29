"""Run the real wizard button dispatch; installing must not rebuild a large game."""
from pathlib import Path
import subprocess
import tempfile
import unittest


class BuildInstallFlowTest(unittest.TestCase):
    def test_reuses_completed_apk(self):
        root = Path(__file__).resolve().parents[1]
        source = (root / 'app/src/main/java/com/zenithuzu/rpgmkport/MainActivity.java').read_text()
        method = source.split('    private void siguiente()', 1)[1].split('    private void mostrar(', 1)[0]
        # Stop before the next method, including its comment/annotation if any.
        method = method[:method.rfind('}')+1]
        harness = '''
import java.io.File;
class WizardCheck {
 boolean mOcupado;int mPaso=3,PASOS=3,builds,installs;File mApk;
 void crear(){builds++;} void instalar(File apk){installs++;} void guardarDatos(){}void mostrar(int step){}
 private void siguiente() METHOD
 public static void main(String[] args)throws Exception {
  WizardCheck w=new WizardCheck();w.siguiente();if(w.builds!=1 || w.installs!=0)throw new AssertionError("initial build");
  w.mApk=new File(args[0]);w.mApk.createNewFile();
  w.siguiente();w.siguiente();if(w.builds!=1 || w.installs!=2)throw new AssertionError("rebuilt completed APK");
  w.mOcupado=true;w.siguiente();if(w.installs!=2)throw new AssertionError("busy dispatch");
  w.mOcupado=false;w.mApk.delete();w.siguiente();if(w.builds!=2)throw new AssertionError("missing APK");
 }
}
'''.replace('METHOD', method)
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'WizardCheck.java'
            path.write_text(harness)
            result = subprocess.run(['java', str(path), str(Path(tmp)/'game.apk')], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == '__main__':
    unittest.main()
