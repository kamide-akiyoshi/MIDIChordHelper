package camidion.chordhelper.pianokeyboard;

import java.awt.event.InputEvent;

import javax.swing.event.ChangeEvent;

public abstract class PianoKeyboardAdapter implements PianoKeyboardListener {
	public void pianoKeyPressed(int n, InputEvent e) { }
	public void pianoKeyReleased(int n, InputEvent e) { }
	public void octaveMoved(ChangeEvent e) { }
	public void octaveResized(ChangeEvent e) { }
}