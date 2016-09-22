package camidion.chordhelper.music;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

// 一般のトラック（メロディ、ドラム共通）
//
public abstract class AbstractNoteTrackSpec extends AbstractTrackSpec {
	public int midiChannel = -1;
	public int programNumber = -1;
	public int velocity = 64;

	public AbstractNoteTrackSpec() {}
	public AbstractNoteTrackSpec(int ch) {
		midiChannel = ch;
	}
	public AbstractNoteTrackSpec(int ch, String name) {
		midiChannel = ch;
		this.name = name;
	}
	public AbstractNoteTrackSpec(int ch, String name, int programNumber) {
		this(ch,name);
		this.programNumber = programNumber;
	}
	public AbstractNoteTrackSpec(int ch, String name, int programNumber, int velocity) {
		this(ch,name,programNumber);
		this.velocity = velocity;
	}
	public Track createTrack( Sequence seq, FirstTrackSpec firstTrackSpec ) {
		Track track = super.createTrack( seq, firstTrackSpec );
		if( programNumber >= 0 ) addProgram( programNumber, 0 );
		return track;
	}
	public boolean addProgram( int program_no, long tickPos ) {
		ShortMessage shortMsg;
		try {
			(shortMsg = new ShortMessage()).setMessage(ShortMessage.PROGRAM_CHANGE, midiChannel, program_no, 0);
		} catch( InvalidMidiDataException ex ) {
			ex.printStackTrace();
			return false;
		}
		return track.add(new MidiEvent( (MidiMessage)shortMsg, tickPos ));
	}
	public boolean addNote(long startTickPos, long endTickPos, int noteNumber) {
		return addNote(startTickPos, endTickPos, noteNumber, velocity);
	}
	public boolean addNote(long startTickPos, long endTickPos, int noteNumber, int velocity) {
		ShortMessage short_msg;
		try {
			(short_msg = new ShortMessage()).setMessage(ShortMessage.NOTE_ON, midiChannel, noteNumber, velocity);
		} catch( InvalidMidiDataException ex ) {
			ex.printStackTrace();
			return false;
		}
		if( ! track.add(new MidiEvent( (MidiMessage)short_msg, startTickPos )) ) return false;
		try {
			(short_msg = new ShortMessage()).setMessage(ShortMessage.NOTE_OFF, midiChannel, noteNumber, velocity);
		} catch( InvalidMidiDataException ex ) {
			ex.printStackTrace();
			return false;
		}
		return track.add( new MidiEvent( (MidiMessage)short_msg, endTickPos ) );
	}
}