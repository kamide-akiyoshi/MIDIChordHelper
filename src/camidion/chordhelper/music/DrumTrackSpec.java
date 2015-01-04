package camidion.chordhelper.music;

import javax.swing.ComboBoxModel;
import javax.swing.event.ListDataListener;

public class DrumTrackSpec extends AbstractNoteTrackSpec {
	public static int defaultPercussions[] = { // ドラムの音色リスト
		36, // Bass Drum 1
		44, // Pedal Hi-Hat
		39, // Hand Clap
		48, // Hi Mid Tom
		50, // High Tom
		38, // Acoustic Snare
		62, // Mute Hi Conga
		63, // Open Hi Conga
	};
	public PercussionComboBoxModel models[]
		= new PercussionComboBoxModel[defaultPercussions.length];
	public int[] beat_patterns = {
		0x8888, 0x2222, 0x0008, 0x0800,
		0, 0, 0, 0
	};
	public class PercussionComboBoxModel implements ComboBoxModel<String> {
		private int noteNumber;
		public PercussionComboBoxModel(int defaultNoteNumber) {
			noteNumber = defaultNoteNumber;
		}
		public int getSelectedNoteNo() {
			return noteNumber;
		}
		public void setSelectedNoteNo(int noteNumber) {
			this.noteNumber = noteNumber;
		}
		@Override
		public Object getSelectedItem() {
			return noteNumber + ": " + MIDISpec.getPercussionName(noteNumber);
		}
		@Override
		public void setSelectedItem(Object anItem) {
			String name = (String)anItem;
			int i = MIDISpec.MIN_PERCUSSION_NUMBER;
			for( String pname : MIDISpec.PERCUSSION_NAMES ) {
				if( name.equals(i + ": " + pname) ) {
					noteNumber = i; return;
				}
				i++;
			}
		}
		@Override
		public String getElementAt(int index) {
			return (index + MIDISpec.MIN_PERCUSSION_NUMBER) + ": "
					+ MIDISpec.PERCUSSION_NAMES[index];
		}
		@Override
		public int getSize() {
			return MIDISpec.PERCUSSION_NAMES.length;
		}
		@Override
		public void addListDataListener(ListDataListener l) { }
		@Override
		public void removeListDataListener(ListDataListener l) { }
	}

	public DrumTrackSpec(int ch, String name) {
		super(ch,name);
		for( int i=0; i<defaultPercussions.length; i++ ) {
			models[i] = new PercussionComboBoxModel(defaultPercussions[i]);
		}
	}

	public void addDrums( ChordProgression cp ) {
		int i;
		long tick;
		for( ChordProgression.Line line : cp.lines ) { // 行単位の処理
			for( ChordProgression.Measure measure : line ) { // 小節単位の処理
				if( measure.ticks_per_beat == null )
					continue;
				ChordProgression.TickRange range = measure.getRange();
				int mask;
				for(
						tick = range.start_tick_pos, mask = 0x8000;
						tick < range.end_tick_pos;
						tick += minNoteTicks, mask >>>= 1
						) {
					for( i=0; i<beat_patterns.length; i++ ) {
						if( (beat_patterns[i] & mask) == 0 )
							continue;
						addNote(
								tick, tick+10,
								models[i].getSelectedNoteNo(),
								velocity
								);
					}
				}
			}
		}
	}
}