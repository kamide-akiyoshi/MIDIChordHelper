package camidion.chordhelper.pianokeyboard;

import java.awt.event.InputEvent;
import java.util.EventListener;

import javax.swing.event.ChangeEvent;

public interface PianoKeyboardListener extends EventListener {
	void pianoKeyPressed(int noteNumber, InputEvent event);
	void pianoKeyReleased(int noteNumber, InputEvent event);
	void octaveMoved(ChangeEvent e);
	void octaveResized(ChangeEvent e);
}