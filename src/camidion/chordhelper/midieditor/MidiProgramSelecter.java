package camidion.chordhelper.midieditor;

import javax.swing.JComboBox;

import camidion.chordhelper.music.MIDISpec;

/**
 * MIDI Instrument (Program) - 音色選択
 */
public class MidiProgramSelecter extends JComboBox<String> {
	private int family;
	private MidiProgramFamilySelecter family_selecter = null;
	public MidiProgramSelecter() {
		setFamily(-1);
	}
	public void setFamilySelecter( MidiProgramFamilySelecter mpfs ) {
		family_selecter = mpfs;
	}
	public void setFamily( int family ) {
		int program_no = getProgram();
		this.family = family;
		removeAllItems();
		if( family < 0 ) {
			setMaximumRowCount(16);
			for( int i=0; i < MIDISpec.instrumentNames.length; i++ ) {
				addItem(i+": " + MIDISpec.instrumentNames[i]);
			}
			setSelectedIndex(program_no);
		}
		else {
			setMaximumRowCount(8);
			for( int i=0; i < 8; i++ ) {
				program_no = i + family * 8;
				addItem( program_no + ": " + MIDISpec.instrumentNames[program_no] );
			}
			setSelectedIndex(0);
		}
	}
	public int getProgram() {
		int program_no = getSelectedIndex();
		if( family > 0 && program_no >= 0 ) program_no += family * 8;
		return program_no;
	}
	public String getProgramName() { return (String)( getSelectedItem() ); }
	public void setProgram( int program_no ) {
		if( getItemCount() == 0 ) return; // To ignore event triggered by removeAllItems()
		if( family >= 0 && program_no >= 0 && family == program_no / 8 ) {
			setSelectedIndex(program_no % 8);
		}
		else {
			if( family >= 0 ) setFamily(-1);
			if( family_selecter != null ) family_selecter.setSelectedIndex(0);
			if( program_no < getItemCount() ) setSelectedIndex(program_no);
		}
	}
}