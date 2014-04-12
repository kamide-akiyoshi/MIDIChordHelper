package camidion.chordhelper.midieditor;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComboBox;

import camidion.chordhelper.music.MIDISpec;

/**
 * MIDI Instrument (Program) Family - 音色ファミリーの選択
 */
public class MidiProgramFamilySelecter extends JComboBox<String> implements ActionListener {
	private MidiProgramSelecter programSelecter = null;
	public MidiProgramFamilySelecter() { this(null); }
	public MidiProgramFamilySelecter( MidiProgramSelecter mps ) {
		programSelecter = mps;
		setMaximumRowCount(17);
		addItem("Program:");
		for( int i=0; i < MIDISpec.instrument_family_names.length; i++ ) {
			addItem( (i*8) + "-" + (i*8+7) + ": " + MIDISpec.instrument_family_names[i] );
		}
		setSelectedIndex(0);
		addActionListener(this);
	}
	public void actionPerformed(ActionEvent event) {
		if( programSelecter == null ) return;
		int i = getSelectedIndex();
		programSelecter.setFamily( i < 0 ? i : i-1 );
	}
	public int getProgram() {
		int i = getSelectedIndex();
		if( i <= 0 ) return -1;
		else return (i-1)*8;
	}
	public String getProgramFamilyName() { return (String)( getSelectedItem() ); }
	public void setProgram( int programNumber ) {
		if( programNumber < 0 ) programNumber = 0;
		else programNumber = programNumber / 8 + 1;
		setSelectedIndex( programNumber );
	}
}