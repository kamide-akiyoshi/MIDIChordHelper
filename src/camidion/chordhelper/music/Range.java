package camidion.chordhelper.music;


/**
 * 音域を表すクラスです。
 */
public class Range {
	public int minNote = 0;
	public int maxNote = MIDISpec.MAX_NOTE_NO;
	public int minKeyOffset = 0;
	public boolean isInversionMode = true;
	public Range(int minNote, int maxNote) {
		this.minNote = minNote;
		this.maxNote = maxNote;
	}
	public Range(Integer[] notes) {
		if( notes == null ) return;
		switch( notes.length ) {
		case 0: return;
		case 1: minNote = maxNote = notes[0]; break;
		default:
			if( notes[0] > notes[1] ) {
				minNote = notes[1];
				maxNote = notes[0];
			}
			else {
				minNote = notes[0];
				maxNote = notes[1];
			}
			break;
		}
	}
	public Range(int minNote, int maxNote, int minKeyOffset, boolean invMode) {
		this.minNote = minNote;
		this.maxNote = maxNote;
		this.minKeyOffset = minKeyOffset;
		this.isInversionMode = invMode;
	}
	public int invertedNoteOf(int noteNumber) {
		return invertedNoteOf(noteNumber, null);
	}
	public int invertedNoteOf(int noteNumber, Key key) {
		int minNote = this.minNote;
		int maxNote = this.maxNote;
		int offset = 0;
		if( key != null ) {
			offset = key.relativeDo();
			if( minKeyOffset < 0 && offset >= Note.mod12(minKeyOffset) ) {
				offset -= 12;
			}
			else if( minKeyOffset > 0 && offset < Note.mod12(minKeyOffset) ) {
				offset += 12;
			}
			minNote += offset;
			maxNote += offset;
		}
		int octave = minNote / Note.SEMITONES_PER_OCTAVE;
		noteNumber += 12 * octave;
		while( noteNumber > maxNote )
			noteNumber -= Note.SEMITONES_PER_OCTAVE;
		while( noteNumber > MIDISpec.MAX_NOTE_NO )
			noteNumber -= Note.SEMITONES_PER_OCTAVE;
		while( noteNumber < minNote )
			noteNumber += Note.SEMITONES_PER_OCTAVE;
		while( noteNumber < 0 )
			noteNumber += Note.SEMITONES_PER_OCTAVE;
		return noteNumber;
	}
	public void invertNotesOf(int[] notes, Key key) {
		int i;
		if( isInversionMode ) {
			for( i=0; i<notes.length; i++ ) notes[i] = invertedNoteOf(notes[i], key);
		}
		else {
			int n = invertedNoteOf(notes[0], new Key(minKeyOffset));
			int n_diff = n - notes[0];
			notes[0] = n;
			for( i=1; i<notes.length; i++ ) notes[i] += n_diff;
		}
	}
}