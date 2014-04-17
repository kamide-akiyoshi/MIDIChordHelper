package camidion.chordhelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JLabel;

import camidion.chordhelper.chordmatrix.ChordMatrix;
import camidion.chordhelper.music.Chord;
import camidion.chordhelper.music.MIDISpec;
import camidion.chordhelper.music.Music;
import camidion.chordhelper.music.NoteSymbol;
import camidion.chordhelper.pianokeyboard.PianoKeyboard;

/**
 * 和音表示ラベル
 */
public class ChordDisplayLabel extends JLabel implements MouseListener {
	private String defaultString = null;
	private Chord chord = null;
	private int noteNumber = -1;
	private boolean isDark = false;
	private boolean isMouseEntered = false;
	/**
	 * 和音表示ラベルを構築します。
	 * @param defaultString 初期表示する文字列
	 * @param chordMatrix このラベルをクリックしたときに鳴らす和音ボタンマトリクス
	 * @param keyboard このラベルをクリックしたときに鳴らす鍵盤
	 */
	public ChordDisplayLabel(String defaultString, ChordMatrix chordMatrix, PianoKeyboard keyboard) {
		super(defaultString, JLabel.CENTER);
		this.defaultString = defaultString;
		this.keyboard = keyboard;
		if( (this.chordMatrix = chordMatrix) != null ) {
			addMouseListener(this);
			addMouseWheelListener(chordMatrix);
		}
	}
	@Override
	public void paint(Graphics g) {
		super.paint(g);
		Dimension d = getSize();
		if( isMouseEntered && (noteNumber >= 0 || chord != null) ) {
			g.setColor(Color.gray);
			g.drawRect( 0, 0, d.width-1, d.height-1 );
		}
	}
	private PianoKeyboard keyboard = null;
	private ChordMatrix chordMatrix = null;
	@Override
	public void mousePressed(MouseEvent e) {
		if( chord != null ) { // コードが表示されている場合
			if( (e.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0 ) {
				// 右クリックでコードを止める
				chordMatrix.setSelectedChord((Chord)null);
			}
			else {
				// コードを鳴らす。
				//   キーボードが指定されている場合、オリジナルキー（カポ反映済）のコードを使う。
				if( keyboard == null )
					chordMatrix.setSelectedChord(chord);
				else
					chordMatrix.setSelectedChordCapo(chord);
			}
		}
		else if( noteNumber >= 0 ) { // 音階が表示されている場合
			keyboard.noteOn(noteNumber);
		}
	}
	@Override
	public void mouseReleased(MouseEvent e) {
		if( noteNumber >= 0 ) keyboard.noteOff(noteNumber);
	}
	@Override
	public void mouseEntered(MouseEvent e) { mouseEntered(true); }
	@Override
	public void mouseExited(MouseEvent e) { mouseEntered(false); }
	@Override
	public void mouseClicked(MouseEvent e) {
	}
	private void mouseEntered(boolean isMouseEntered) {
		this.isMouseEntered = isMouseEntered;
		if( noteNumber >= 0 || chord != null ) repaint();
	}
	/**
	 * 音階を表示します。
	 * @param noteNumber MIDIノート番号
	 * @param isRhythmPart リズムパートのときtrue
	 */
	public void setNote(int noteNumber, boolean isRhythmPart) {
		setToolTipText(null);
		this.chord = null;
		if( (this.noteNumber = noteNumber) < 0 ) {
			setText(defaultString);
			return;
		}
		if( isRhythmPart ) {
			String pn = MIDISpec.getPercussionName(noteNumber);
			setText("MIDI note No." + noteNumber + " : " + pn);
		}
		else {
			String ns = NoteSymbol.noteNoToSymbol(noteNumber);
			double f = Music.noteNumberToFrequency(noteNumber);
			setText("Note: "+ns+"  -  MIDI note No."+noteNumber+" : "+Math.round(f)+"Hz");
		}
	}
	/**
	 * 和音（コード名）を表示します。
	 * @param chord 和音
	 */
	public void setChord(Chord chord) {
		this.noteNumber = -1;
		if( (this.chord = chord) == null ) {
			setText(defaultString);
			setToolTipText(null);
		}
		else {
			setChordText();
			setToolTipText("Chord: "+chord.toName());
		}
	}
	/**
	 * 表示をクリアします。
	 */
	public void clear() { setNote(-1, false); }
	/**
	 * ダークモードのON/OFFを切り替えます。
	 * @param isDark ダークモードONのときtrue
	 */
	public void setDarkMode(boolean isDark) {
		this.isDark = isDark;
		if( chord != null ) setChordText();
	}
	private void setChordText() {
		setText(chord.toHtmlString(isDark ? "#FFCC33" : "maroon"));
	}
}