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
	public AbstractNoteTrackSpec(int ch, String name, int program_no) {
		this(ch,name);
		this.programNumber = program_no;
	}
	public AbstractNoteTrackSpec(int ch, String name, int program_no, int velocity) {
		this(ch,name,program_no);
		this.velocity = velocity;
	}
	public Track createTrack( Sequence seq, FirstTrackSpec first_track_spec ) {
		Track track = super.createTrack( seq, first_track_spec );
		if( programNumber >= 0 ) addProgram( programNumber, 0 );
		return track;
	}
	public boolean addProgram( int program_no, long tick_pos ) {
		ShortMessage short_msg;
		try {
			(short_msg = new ShortMessage()).setMessage(
				ShortMessage.PROGRAM_CHANGE, midiChannel, program_no, 0
			);
		} catch( InvalidMidiDataException ex ) {
			ex.printStackTrace();
			return false;
		}
		return track.add(new MidiEvent( (MidiMessage)short_msg, tick_pos ));
	}
	public boolean addNote(long start_tick_pos, long end_tick_pos, int note_no) {
		return addNote(start_tick_pos, end_tick_pos, note_no, velocity);
	}
	public boolean addNote(
		long start_tick_pos, long end_tick_pos,
		int note_no, int velocity
	) {
		ShortMessage short_msg;
		//
		try {
			(short_msg = new ShortMessage()).setMessage(
				ShortMessage.NOTE_ON, midiChannel, note_no, velocity
			);
		} catch( InvalidMidiDataException ex ) {
			ex.printStackTrace();
			return false;
		}
		if( ! track.add(new MidiEvent( (MidiMessage)short_msg, start_tick_pos )) )
			return false;
		//
		try {
			(short_msg = new ShortMessage()).setMessage(
					ShortMessage.NOTE_OFF, midiChannel, note_no, velocity
					);
		} catch( InvalidMidiDataException ex ) {
			ex.printStackTrace();
			return false;
		}
		return track.add( new MidiEvent( (MidiMessage)short_msg, end_tick_pos ) );
	}
}