package camidion.chordhelper.music;


/**
 * 音域を表すクラスです。
 */
public class Range {
	public int min_note = 0;
	public int max_note = MIDISpec.MAX_NOTE_NO;
	public int min_key_offset = 0;
	public boolean is_inversion_mode = true;
	public Range( int min_note, int max_note ) {
		this.min_note = min_note;
		this.max_note = max_note;
	}
	public Range( Integer[] notes ) {
		if( notes == null ) return;
		switch( notes.length ) {
		case 0: return;
		case 1:
			min_note = max_note = notes[0];
			break;
		default:
			if( notes[0] > notes[1] ) {
				min_note = notes[1];
				max_note = notes[0];
			}
			else {
				min_note = notes[0];
				max_note = notes[1];
			}
			break;
		}
	}
	public Range(
		int min_note, int max_note,
		int min_key_offset, boolean inv_mode
	) {
		this.min_note = min_note;
		this.max_note = max_note;
		this.min_key_offset = min_key_offset;
		this.is_inversion_mode = inv_mode;
	}
	public int invertedNoteOf(int note_no) {
		return invertedNoteOf( note_no, null );
	}
	public int invertedNoteOf(int note_no, Key key) {
		int min_note = this.min_note;
		int max_note = this.max_note;
		int offset = 0;
		if( key != null ) {
			offset = key.relativeDo();
			if( min_key_offset < 0 && offset >= Music.mod12(min_key_offset) ) {
				offset -= 12;
			}
			else if( min_key_offset > 0 && offset < Music.mod12(min_key_offset) ) {
				offset += 12;
			}
			min_note += offset;
			max_note += offset;
		}
		int octave = min_note / Music.SEMITONES_PER_OCTAVE;
		note_no += 12 * octave;
		while( note_no > max_note )
			note_no -= Music.SEMITONES_PER_OCTAVE;
		while( note_no > MIDISpec.MAX_NOTE_NO )
			note_no -= Music.SEMITONES_PER_OCTAVE;
		while( note_no < min_note )
			note_no += Music.SEMITONES_PER_OCTAVE;
		while( note_no < 0 )
			note_no += Music.SEMITONES_PER_OCTAVE;
		return note_no;
	}
	public void invertNotesOf( int[] notes, Key key ) {
		int i;
		if( is_inversion_mode ) {
			for( i=0; i<notes.length; i++ ) {
				notes[i] = invertedNoteOf( notes[i], key );
			}
		}
		else {
			int n = invertedNoteOf( notes[0], new Key(min_key_offset) );
			int n_diff = n - notes[0];
			notes[0] = n;
			for( i=1; i<notes.length; i++ ) {
				notes[i] += n_diff;
			}
		}
	}
}