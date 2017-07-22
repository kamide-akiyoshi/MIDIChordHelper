package camidion.chordhelper;

import javax.swing.JLabel;

import camidion.chordhelper.midieditor.SequenceTrackListTableModel;

public class SongTitleLabel extends JLabel {
	public void clear() {
		setText("<html>[No MIDI file loaded]</html>");
	}
	public void setSongTitle(int songIndex, SequenceTrackListTableModel sequenceModel) {
		String title = sequenceModel.toString();
		String titleHtml = title.isEmpty()?"[Untitled]":"<font color=maroon>"+title+"</font>";
		setText("<html>MIDI file " + songIndex + ": " + titleHtml + "</html>");
	}
}
