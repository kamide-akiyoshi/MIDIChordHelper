
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.EventListener;
import java.util.LinkedList;
import java.util.Vector;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiMessage;
import javax.swing.AbstractAction;
import javax.swing.BoundedRangeModel;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

interface PianoKeyboardListener extends EventListener {
	void pianoKeyPressed(int noteNumber, InputEvent event);
	void pianoKeyReleased(int noteNumber, InputEvent event);
	void octaveMoved(ChangeEvent e);
	void octaveResized(ChangeEvent e);
}
abstract class PianoKeyboardAdapter implements PianoKeyboardListener {
	public void pianoKeyPressed(int n, InputEvent e) { }
	public void pianoKeyReleased(int n, InputEvent e) { }
	public void octaveMoved(ChangeEvent e) { }
	public void octaveResized(ChangeEvent e) { }
}

/**
 * Piano Keyboard class for MIDI Chord Helper
 *
 * @author
 *	Copyright (C) 2004-2013 Akiyoshi Kamide
 *	http://www.yk.rim.or.jp/~kamide/music/chordhelper/
 */
public class PianoKeyboard extends JComponent {
	/**
	 * 最小オクターブ幅
	 */
	public static final int	MIN_OCTAVES = 3;
	/**
	 * 最大オクターブ幅
	 */
	public static final int	MAX_OCTAVES = MIDISpec.MAX_NOTE_NO / 12 + 1;
	/**
	 * 濃いピンク
	 */
	public static final Color DARK_PINK = new Color(0xFF,0x50,0x80);

	/** 白鍵のサイズ */
	Dimension	whiteKeySize;
	/** 黒鍵のサイズ */
	Dimension	blackKeySize;
	/** ダークモードならtrue */
	boolean		isDark = false;
	/** １オクターブあたりの幅 */
	float widthPerOctave = Music.SEMITONES_PER_OCTAVE * 10;

	/** すべてのピアノキー */
	private PianoKey[] keys;
	/** 黒鍵 */
	private PianoKey[] blackKeys;
	/** 白鍵 */
	private PianoKey[] whiteKeys;
	/**
	 * オクターブ範囲モデル
	 */
	DefaultBoundedRangeModel octaveRangeModel;
	/**
	 * オクターブ幅モデル
	 */
	DefaultBoundedRangeModel octaveSizeModel;
	/**
	 * ベロシティモデル
	 */
	BoundedRangeModel velocityModel = new DefaultBoundedRangeModel(64, 0, 0, 127);
	/**
	 * MIDIチャンネル選択コンボボックスモデル
	 */
	DefaultMidiChannelComboBoxModel
		midiChComboboxModel = new DefaultMidiChannelComboBoxModel();

	/**
	 * ノートのリスト。配列の要素として使えるようクラス名を割り当てます。
	 */
	class NoteList extends LinkedList<Integer> {
		// 何もすることはない
	}
	/**
	 * 選択マーク●がついている鍵を表すノートリスト
	 */
	private NoteList selectedKeyNoteList = new NoteList();
	/**
	 * 調号（スケール判定用）
	 */
	private Music.Key	keySignature = null;
	/**
	 * 表示中のコード
	 */
	private Music.Chord	chord = null;
	/**
	 * コードボタンマトリクス
	 */
	ChordMatrix chordMatrix;
	/**
	 * コード表示部
	 */
	ChordDisplay chordDisplay;
	/**
	 * Innocence「あの楽器」
	 */
	AnoGakkiLayeredPane anoGakkiLayeredPane;
	/**
	 * MIDIチャンネルをボタンで選択
	 */
	MidiChannelButtonSelecter midiChannelButtonSelecter;

	private NoteList[] channelNotes = new NoteList[MIDISpec.MAX_CHANNELS];
	private int[] pitchBendValues = new int[MIDISpec.MAX_CHANNELS];
	private int[] pitchBendSensitivities = new int[MIDISpec.MAX_CHANNELS];
	private int[] modulations = new int[MIDISpec.MAX_CHANNELS];

	/**
	 * この鍵盤の仮想MIDIデバイスです。
	 * ノートオンなどのMIDIメッセージを受け取り、画面に反映します。
	 */
	VirtualMidiDevice midiDevice = new AbstractVirtualMidiDevice() {
		class MyInfo extends Info {
			protected MyInfo() {
				super("MIDI Keyboard","Unknown vendor","Software MIDI keyboard","");
			}
		}
		/**
		 * MIDIデバイス情報
		 */
		protected MyInfo info;
		@Override
		public Info getDeviceInfo() { return info; }
		{
			info = new MyInfo();
			// 受信してMIDIチャンネルの状態を管理する
			setReceiver(new AbstractMidiStatus() {{
				for( int i=0; i<MIDISpec.MAX_CHANNELS; i++ )
					add(new MidiChannelStatus(i));
			}});
		}
		class MidiChannelStatus extends AbstractMidiChannelStatus {
			public MidiChannelStatus(int channel) {
				super(channel);
				channelNotes[channel] = new NoteList();
				pitchBendSensitivities[channel] = 2; // Default is wholetone = 2 semitones
			}
			@Override
			public void fireRpnChanged() {
				if( dataFor != DATA_FOR_RPN ) return;

				// RPN (MSB) - Accept 0x00 only
				if( controllerValues[0x65] != 0x00 ) return;

				// RPN (LSB)
				switch( controllerValues[0x64] ) {
				case 0x00: // Pitch Bend Sensitivity
					if( controllerValues[0x06] == 0 ) return;
					pitchBendSensitivities[channel] = controllerValues[0x06];
					break;
				}
			}
			@Override
			public void noteOff(int noteNumber, int velocity) {
				noteOff(noteNumber);
			}
			@Override
			public void noteOff(int noteNumber) {
				keyOff( channel, noteNumber );
				if( chordMatrix != null ) {
					if( ! isRhythmPart() )
						chordMatrix.note(false, noteNumber);
				}
				if( midiChannelButtonSelecter != null ) {
					midiChannelButtonSelecter.repaint();
				}
			}
			@Override
			public void noteOn(int noteNumber, int velocity) {
				if( velocity <= 0 ) {
					noteOff(noteNumber); return;
				}
				keyOn( channel, noteNumber );
				if( midiChComboboxModel.getSelectedChannel() == channel ) {
					if( chordDisplay != null ) {
						if( chordMatrix != null && chordMatrix.isPlaying() )
							chordDisplay.setNote(-1);
						else
							chordDisplay.setNote(noteNumber, isRhythmPart());
					}
					if( anoGakkiLayeredPane != null ) {
						PianoKey piano_key = getPianoKey(noteNumber);
						if( piano_key != null )
							anoGakkiLayeredPane.start(PianoKeyboard.this, piano_key.indicator);
					}
				}
				if( chordMatrix != null ) {
					if( ! isRhythmPart() )
						chordMatrix.note(true, noteNumber);
				}
				if( midiChannelButtonSelecter != null ) {
					midiChannelButtonSelecter.repaint();
				}
			}
			@Override
			public void allNotesOff() {
				allKeysOff( channel, -1 );
				if( chordMatrix != null )
					chordMatrix.clearIndicators();
			}
			@Override
			public void setPitchBend(int bend) {
				super.setPitchBend(bend);
				pitchBendValues[channel] = bend;
				repaintNotes();
			}
			@Override
			public void resetAllControllers() {
				super.resetAllControllers();
				//
				// See also: Response to Reset All Controllers
				//     http://www.midi.org/about-midi/rp15.shtml
				//
				pitchBendValues[channel] = MIDISpec.PITCH_BEND_NONE;
				modulations[channel] = 0;
				repaintNotes();
			}
			@Override
			public void controlChange(int controller, int value) {
				super.controlChange(controller,value);
				switch( controller ) {
				case 0x01: // Moduration (MSB)
					modulations[channel] = value;
					repaintNotes();
					break;
				}
			}
			private void repaintNotes() {
				if( midiChComboboxModel.getSelectedChannel() != channel
					|| channelNotes[channel] == null
				)
					return;
				if( channelNotes[channel].size() > 0 || selectedKeyNoteList.size() > 0 )
					repaint();
			}
		}
	};

	/**
	 * 現在選択中のMIDIチャンネルを返します。
	 * @return 現在選択中のMIDIチャンネル
	 */
	public MidiChannel getSelectedChannel() {
		return midiDevice.getChannels()[midiChComboboxModel.getSelectedChannel()];
	}
	/**
	 * 現在選択中のMIDIチャンネルにノートオンメッセージを送出します。
	 * ベロシティ値は現在画面で設定中の値となります。
	 * @param noteNumber ノート番号
	 */
	public void noteOn(int noteNumber) {
		getSelectedChannel().noteOn(noteNumber, velocityModel.getValue());
	}
	/**
	 * 現在選択中のMIDIチャンネルにノートオフメッセージを送出します。
	 * ベロシティ値は現在画面で設定中の値となります。
	 * @param noteNumber ノート番号
	 */
	public void noteOff(int noteNumber) {
		getSelectedChannel().noteOff(noteNumber, velocityModel.getValue());
	}

	/**
	 * １個のピアノ鍵盤を表す矩形
	 */
	private class PianoKey extends Rectangle {
		private boolean isBlack = false;
		private int position = 0;
		private String bindedKeyChar = null;
		private Rectangle indicator;
		private boolean outOfBounds = false;
		public PianoKey(Point p, Dimension d, Dimension indicatorSize) {
			super(p,d);
			Point indicatorPosition = new Point(
				p.x + (d.width - indicatorSize.width) / 2,
				p.y + d.height - indicatorSize.height - indicatorSize.height / 2 + 2
			);
			indicator = new Rectangle(indicatorPosition, indicatorSize);
		}
		int getNote(int chromaticOffset) {
			int n = position + chromaticOffset;
			return (outOfBounds = ( n > MIDISpec.MAX_NOTE_NO )) ? -1 : n;
		}
		boolean paintKey(Graphics2D g2, boolean isPressed) {
			if(outOfBounds) return false;
			g2.fill3DRect(x, y, width, height, !isPressed);
			return true;
		}
		boolean paintKey(Graphics2D g2) {
			return paintKey(g2,false);
		}
		boolean paintKeyBinding(Graphics2D g2) {
			if( bindedKeyChar == null ) return false;
			g2.drawString( bindedKeyChar, x + width/3, indicator.y - 2 );
			return true;
		}
		boolean paintIndicator(Graphics2D g2, boolean is_small, int pitch_bend_value) {
			if( is_small ) {
				g2.fillOval(
					indicator.x + indicator.width/4,
					indicator.y + indicator.height/4 + 1,
					indicator.width/2,
					indicator.height/2
				);
			}
			else {
				int current_channel = midiChComboboxModel.getSelectedChannel();
				int sens = pitchBendSensitivities[current_channel];
				if( sens == 0 ) {
					sens = 2;
				}
				int x_offset = (
					7 * whiteKeySize.width * sens * (pitch_bend_value - MIDISpec.PITCH_BEND_NONE)
				) / (12 * 8192);
				int additional_height = indicator.height * modulations[current_channel] / 256 ;
				int y_offset = additional_height / 2 ;
				g2.fillOval(
					indicator.x + ( x_offset < 0 ? x_offset : 0 ),
					indicator.y - y_offset,
					indicator.width + ( x_offset < 0 ? -x_offset : x_offset ),
					indicator.height + additional_height
				);
			}
			return true;
		}
		boolean paintIndicator(Graphics2D g2, boolean is_small) {
			return paintIndicator( g2, is_small, 0 );
		}
	}

	private class MouseKeyListener
		implements MouseListener, MouseMotionListener, KeyListener
	{
		private void pressed(int c, int n, InputEvent e) {
			keyOn(c,n);
			noteOn(n);
			firePianoKeyPressed(n,e);
		}
		private void released(int c, int n, InputEvent e) {
			keyOff(c,n);
			noteOff(n);
			firePianoKeyReleased(n,e);
		}
		@Override
		public void mousePressed(MouseEvent e) {
			int n = getNote(e.getPoint());
			if( n < 0 ) return;
			int c = midiChComboboxModel.getSelectedChannel();
			if( channelNotes[c].contains(n) ) return;
			chord = null;
			pressed(c,n,e);
			requestFocusInWindow();
			repaint();
		}
		@Override
		public void mouseReleased(MouseEvent e) {
			int c = midiChComboboxModel.getSelectedChannel();
			NoteList nl = channelNotes[c];
			if( ! nl.isEmpty() )
				released(c, nl.poll(), e);
		}
		@Override
		public void mouseEntered(MouseEvent e) {
		}
		@Override
		public void mouseExited(MouseEvent e) {
		}
		@Override
		public void mouseDragged(MouseEvent e) {
			int n = getNote(e.getPoint());
			if( n < 0 ) return;
			int c = midiChComboboxModel.getSelectedChannel();
			NoteList nl = channelNotes[c];
			if( nl.contains(n) ) return;
			if( ! nl.isEmpty() )
				released(c, nl.poll(), e);
			pressed(c,n,e);
		}
		@Override
		public void mouseMoved(MouseEvent e) {
		}
		@Override
		public void mouseClicked(MouseEvent e) {
		}
		@Override
		public void keyPressed(KeyEvent e) {
			int kc = e.getKeyCode();
			if( kc == KeyEvent.VK_LEFT || kc == KeyEvent.VK_KP_LEFT ) {
				octaveRangeModel.setValue( octaveRangeModel.getValue() - 1 );
				return;
			}
			else if( kc == KeyEvent.VK_RIGHT || kc == KeyEvent.VK_KP_RIGHT ) {
				octaveRangeModel.setValue( octaveRangeModel.getValue() + 1 );
				return;
			}
			int n = getNote(e); if( n < 0 ) return;
			int c = midiChComboboxModel.getSelectedChannel();
			if( channelNotes[c].contains(n) ) return;
			chord = null;
			pressed(c,n,e);
		}
		@Override
		public void keyReleased(KeyEvent e) {
			int c = midiChComboboxModel.getSelectedChannel();
			int n = getNote(e);
			if( n < 0 || ! channelNotes[c].contains(n) ) return;
			released(c,n,e);
		}
		@Override
		public void keyTyped(KeyEvent e) {
		}
	}

	/**
	 * 新しいピアノキーボードを構築します。
	 */
	public PianoKeyboard() {
		setLayout(new BorderLayout());
		setFocusable(true);
		addFocusListener(new FocusListener() {
			public void focusGained(FocusEvent e) { repaint(); }
			public void focusLost(FocusEvent e)   { repaint(); }
		});
		MouseKeyListener mkl = new MouseKeyListener();
		addMouseListener(mkl);
		addMouseMotionListener(mkl);
		addKeyListener(mkl);
		int octaves = getPerferredOctaves();
		octaveSizeModel = new DefaultBoundedRangeModel(
			octaves, 0, MIN_OCTAVES, MAX_OCTAVES
		) {{
			addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					fireOctaveResized(e);
					octaveSizeChanged();
				}
			});
		}};
		octaveRangeModel = new DefaultBoundedRangeModel(
			(MAX_OCTAVES - octaves) / 2, octaves, 0, MAX_OCTAVES
		) {{
			addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					fireOctaveMoved(e);
					checkOutOfBounds();
					repaint();
				}
			});
		}};
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				octaveSizeModel.setValue( getPerferredOctaves() );
				octaveSizeChanged();
			}
		});
		midiChComboboxModel.addListDataListener(
			new ListDataListener() {
				public void contentsChanged(ListDataEvent e) {
					int c = midiChComboboxModel.getSelectedChannel();
					for( int n : channelNotes[c] )
						if( autoScroll(n) ) break;
					repaint();
				}
				public void intervalAdded(ListDataEvent e) {}
				public void intervalRemoved(ListDataEvent e) {}
			}
		);
	}
	public void paint(Graphics g) {
		if( keys == null ) return;
		Graphics2D g2 = (Graphics2D) g;
		Dimension d = getSize();
		//
		// 鍵盤をクリア
		g2.setBackground( getBackground() );
		g2.clearRect( 0, 0, d.width, d.height );
		//
		// 白鍵を描画
		g2.setColor( isDark ? Color.gray : Color.white );
		for( PianoKey k : whiteKeys ) k.paintKey(g2);

		NoteList notesArray[] = {
			(NoteList)selectedKeyNoteList.clone(),
			(NoteList)channelNotes[midiChComboboxModel.getSelectedChannel()].clone()
		};
		PianoKey key;
		//
		// ノートオン状態の白鍵を塗り重ねる
		for( int n : notesArray[1] )
			if( (key=getPianoKey(n)) != null && !(key.isBlack) )
				key.paintKey(g2,true);
		//
		// 黒鍵を描画
		g2.setColor(getForeground());
		for( PianoKey k : blackKeys ) k.paintKey(g2);
		//
		// ノートオン状態の黒鍵を塗り重ねる
		g2.setColor( Color.gray );
		for( int n : notesArray[1] )
			if( (key=getPianoKey(n)) != null && key.isBlack )
				key.paintKey(g2,true);
		//
		// インジケータの表示
		for( NoteList nl : notesArray ) {
			if( nl == null ) continue;
			for( Integer ni : nl ) {
				if( ni == null ) continue;
				int n = ni;
				if( (key=getPianoKey(n)) == null ) continue;
				boolean isOnScale = (keySignature == null || keySignature.isOnScale(n));
				int chordIndex;
				if( chord != null && (chordIndex = chord.indexOf(n)) >=0 ) {
					g2.setColor(Music.Chord.NOTE_INDEX_COLORS[chordIndex]);
				}
				else {
					g2.setColor(isDark && isOnScale ? Color.pink : DARK_PINK);
				}
				int c = midiChComboboxModel.getSelectedChannel();
				key.paintIndicator(g2, false, pitchBendValues[c]);
				if( ! isOnScale ) {
					g2.setColor(Color.white);
					key.paintIndicator(g2, true);
				}
			}
		}
		if( isFocusOwner() ) {
			// Show PC-key binding
			for( PianoKey k : bindedKeys ) {
				g2.setColor(
					k.isBlack ? Color.gray.brighter() :
					isDark ? getForeground() :
					getForeground().brighter()
				);
				k.paintKeyBinding(g2);
			}
		}
	}
	//
	protected void firePianoKeyPressed(int note_no, InputEvent event) {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==PianoKeyboardListener.class) {
				((PianoKeyboardListener)listeners[i+1]).pianoKeyPressed(note_no,event);
			}
		}
	}
	protected void firePianoKeyReleased(int note_no, InputEvent event) {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==PianoKeyboardListener.class) {
				((PianoKeyboardListener)listeners[i+1]).pianoKeyReleased(note_no,event);
			}
		}
	}
	protected void fireOctaveMoved(ChangeEvent event) {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==PianoKeyboardListener.class) {
				((PianoKeyboardListener)listeners[i+1]).octaveMoved(event);
			}
		}
	}
	protected void fireOctaveResized(ChangeEvent event) {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==PianoKeyboardListener.class) {
				((PianoKeyboardListener)listeners[i+1]).octaveResized(event);
			}
		}
	}
	/**
	 * 現在のオクターブ位置における、
	 * 指定のノート番号に対する１個のピアノキーを返します。
	 * @param noteNumber ノート番号
	 * @return ピアノキー（範囲外の場合 null）
	 */
	private PianoKey getPianoKey(int noteNumber) {
		int i = noteNumber - octaveRangeModel.getValue() * 12 ;
		return i>=0 && i<keys.length ? keys[i]: null;
	}
	/**
	 * 指定の座標におけるノート番号を返します。
	 * @param point 座標
	 * @return ノート番号（範囲外の場合 -1）
	 */
	private int getNote(Point point) {
		PianoKey k = getPianoKeyAt(point);
		return k==null ? -1 : k.getNote(getChromaticOffset());
	}
	/**
	 * 指定の座標における１個のピアノキーを返します。
	 * @param point 座標
	 * @return ピアノキー（範囲外の場合 null）
	 */
	private PianoKey getPianoKeyAt(Point point) {
		int indexWhite = point.x / whiteKeySize.width;
		int indexOctave = indexWhite / 7;
		int i = (indexWhite -= indexOctave * 7) * 2 + indexOctave * 12;
		if( indexWhite >= 3 ) i--;
		if( i < 0 || i > keys.length-1 )
			return null;
		if( point.y > blackKeySize.height )
			return keys[i];
		PianoKey k;
		if( i > 0 ) {
			k = keys[i-1];
			if( k.isBlack && !(k.outOfBounds) && k.contains(point) )
				return k;
		}
		if( i < keys.length-1 ) {
			k = keys[i+1];
			if( k.isBlack && !(k.outOfBounds) && k.contains(point) )
				return k;
		}
		return keys[i];
	}

	private PianoKey[] bindedKeys;
	private int		bindedKeyPosition;
	private String	bindedKeyChars;
	private PianoKey getPianoKey(KeyEvent e) {
		int i = bindedKeyChars.indexOf(e.getKeyChar());
		return i >= 0 ? keys[bindedKeyPosition + i] : null;
	}
	private int getNote(KeyEvent e) {
		PianoKey k = getPianoKey(e);
		return k==null ? -1 : k.getNote(getChromaticOffset());
	}
	private void changeKeyBinding(int from, String keyChars) {
		PianoKey k;
		bindedKeys = new PianoKey[(bindedKeyChars = keyChars).length()];
		bindedKeyPosition = from;
		for( int i = 0; i < bindedKeyChars.length(); i++ ) {
			bindedKeys[i] = k = keys[ bindedKeyPosition + i ];
			k.bindedKeyChar = bindedKeyChars.substring( i, i+1 );
		}
		repaint();
	}

	private void checkOutOfBounds() {
		if( keys == null ) return;
		for( PianoKey k : keys ) k.getNote(getChromaticOffset());
	}
	private void keyOff(int ch, int noteNumber) {
		if( noteNumber < 0 || ch < 0 || ch >= channelNotes.length ) return;
		channelNotes[ch].remove((Object)noteNumber);
		if( ch == midiChComboboxModel.getSelectedChannel() )
			repaint();
	}
	private void keyOn(int ch, int noteNumber) {
		if( noteNumber < 0 || ch < 0 || ch >= channelNotes.length ) return;
		channelNotes[ch].add(noteNumber);
		setSelectedNote(ch,noteNumber);
	}
	public boolean autoScroll(int noteNumber) {
		if( octaveRangeModel == null || keys == null )
			return false;
		int i = noteNumber - getChromaticOffset();
		if( i < 0 ) {
			octaveRangeModel.setValue(
				octaveRangeModel.getValue() - (-i)/Music.SEMITONES_PER_OCTAVE - 1
			);
			return true;
		}
		if( i >= keys.length ) {
			octaveRangeModel.setValue(
				octaveRangeModel.getValue() + (i-keys.length)/Music.SEMITONES_PER_OCTAVE + 1
			);
			return true;
		}
		return false;
	}
	void addPianoKeyboardListener(PianoKeyboardListener l) {
		listenerList.add(PianoKeyboardListener.class, l);
	}
	void removePianoKeyboardListener(PianoKeyboardListener l) {
		listenerList.remove(PianoKeyboardListener.class, l);
	}
	int countKeyOn() {
		return channelNotes[midiChComboboxModel.getSelectedChannel()].size();
	}
	int countKeyOn(int ch) {
		return channelNotes[ch].size();
	}
	void allKeysOff(int ch, int numMarks) {
		if( ! selectedKeyNoteList.isEmpty() ) return;
		switch(numMarks) {
		case -1:
			selectedKeyNoteList = (NoteList)(channelNotes[ch].clone());
			break;
		case  1:
			selectedKeyNoteList.add(
				channelNotes[ch].get(channelNotes[ch].size()-1)
			);
			break;
		default: break;
		}
		channelNotes[ch].clear();
		if( midiChComboboxModel.getSelectedChannel() == ch )
			repaint();
	}
	void clear() {
		selectedKeyNoteList.clear();
		channelNotes[midiChComboboxModel.getSelectedChannel()].clear();
		chord = null;
		repaint();
	}
	int getNote() {
		int current_channel = midiChComboboxModel.getSelectedChannel();
		switch( channelNotes[current_channel].size() ) {
		case 1: return channelNotes[current_channel].get(0);
		case 0:
			if( selectedKeyNoteList.size() == 1 )
				return selectedKeyNoteList.get(0);
			return -1;
		default:
			return -1;
		}
	}
	void setSelectedNote(int noteNumber) {
		setSelectedNote(midiChComboboxModel.getSelectedChannel(), noteNumber);
	}
	void setSelectedNote(int ch, int note_no) {
		if( ch != midiChComboboxModel.getSelectedChannel() )
			return;
		selectedKeyNoteList.add(note_no);
		int maxSel = (chord == null ? maxSelectable : chord.numberOfNotes());
		while( selectedKeyNoteList.size() > maxSel )
			selectedKeyNoteList.poll();
		if( !autoScroll(note_no) ) {
			// When autoScroll() returned false, stateChanged() not invoked - need repaint()
			repaint();
		}
	}
	Integer[] getSelectedNotes() {
		return selectedKeyNoteList.toArray(new Integer[0]);
	}
	Music.Chord getChord() { return chord; }
	void setChord(Music.Chord c) {
		chordDisplay.setChord(chord = c);
	}
	void setKeySignature(Music.Key ks) {
		keySignature = ks;
		repaint();
	}
	private int	maxSelectable = 1;
	void setMaxSelectable( int maxSelectable ) {
		this.maxSelectable = maxSelectable;
	}
	int getMaxSelectable() { return maxSelectable; }
	int getChromaticOffset() {
		return octaveRangeModel.getValue() * Music.SEMITONES_PER_OCTAVE ;
	}
	int getOctaves() { return octaveSizeModel.getValue(); }
	private int getPerferredOctaves() {
		int octaves = Math.round( (float)getWidth() / widthPerOctave );
		if( octaves > MAX_OCTAVES ) {
			octaves = MAX_OCTAVES;
		}
		else if( octaves < MIN_OCTAVES ) {
			octaves = MIN_OCTAVES;
		}
		return octaves;
	}
	private void octaveSizeChanged() {
		int octaves = octaveSizeModel.getValue();
		String defaultBindedKeyChars = "zsxdcvgbhnjm,l.;/\\]";
		Dimension keyboard_size = getSize();
		if( keyboard_size.width == 0 ) {
			return;
		}
		whiteKeySize = new Dimension(
			(keyboard_size.width - 1) / (octaves * 7 + 1),
			keyboard_size.height - 1
		);
		blackKeySize = new Dimension(
			whiteKeySize.width * 3 / 4,
			whiteKeySize.height * 3 / 5
		);
		Dimension indicatorSize = new Dimension(
			whiteKeySize.width / 2,
			whiteKeySize.height / 6
		);
		octaveRangeModel.setExtent( octaves );
		octaveRangeModel.setValue( (MAX_OCTAVES - octaves) / 2 );
		widthPerOctave = keyboard_size.width / octaves;
		//
		// Construct piano-keys
		//
		keys = new PianoKey[ octaves * 12 + 1 ];
		Vector<PianoKey> vBlackKeys = new Vector<PianoKey>();
		Vector<PianoKey> vWhiteKeys = new Vector<PianoKey>();
		Point keyPoint = new Point(1,1);
		PianoKey k;
		int i, i12;
		boolean is_CDE = true;
		for( i = i12 = 0; i < keys.length; i++, i12++ ) {
			switch(i12) {
			case 12: is_CDE = true; i12 = 0; break;
			case  5: is_CDE = false; break;
			default: break;
			}
			keyPoint.x = whiteKeySize.width * (
				i / Music.SEMITONES_PER_OCTAVE * 7 + (i12+(is_CDE?1:2))/2
			);
			if( Music.isOnScale(i12,0) ) {
				k = new PianoKey( keyPoint, whiteKeySize, indicatorSize );
				k.isBlack = false;
				vWhiteKeys.add(k);
			}
			else {
				keyPoint.x -= ( (is_CDE?5:12) - i12 )/2 * blackKeySize.width / (is_CDE?3:4);
				k = new PianoKey( keyPoint, blackKeySize, indicatorSize );
				k.isBlack = true;
				vBlackKeys.add(k);
			}
			(keys[i] = k).position = i;
		}
		whiteKeys = vWhiteKeys.toArray(new PianoKey[1]);
		blackKeys = vBlackKeys.toArray(new PianoKey[1]);
		changeKeyBinding(((octaves - 1) / 2) * 12, defaultBindedKeyChars);
		checkOutOfBounds();
	}
	//
	void setDarkMode(boolean isDark) {
		this.isDark = isDark;
		setBackground( isDark ? Color.black : null );
	}
}

class PianoKeyboardPanel extends JPanel {
	PianoKeyboard keyboard;
	private JScrollBar octaveSelecter;
	private JSlider	octaveSizeSlider;
	private JPanel octaveBar;
	public PianoKeyboardPanel() {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		keyboard = new PianoKeyboard() {{
			addPianoKeyboardListener(
				new PianoKeyboardAdapter() {
					@Override
					public void octaveResized(ChangeEvent e) {
						octaveSelecter.setBlockIncrement(getOctaves());
					}
				}
			);
		}};
		add(octaveBar = new JPanel() {{
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			add(octaveSelecter = new JScrollBar(JScrollBar.HORIZONTAL) {
				{
					setToolTipText("Octave position");
					setModel(keyboard.octaveRangeModel);
					setBlockIncrement(keyboard.getOctaves());
				}
			});
			add(Box.createHorizontalStrut(5));
			add(octaveSizeSlider = new JSlider() {
				{
					setToolTipText("Octave size");
					setModel(keyboard.octaveSizeModel);
					setMinimumSize(new Dimension(100, 18));
					setMaximumSize(new Dimension(100, 18));
					setPreferredSize(new Dimension(100, 18));
				}
			});
		}});
		add(keyboard);
		setAlignmentX((float)0.5);
	}
	public void setDarkMode(boolean isDark) {
		Color col = isDark ? Color.black : null;
		octaveSelecter.setBackground( col );
		octaveSizeSlider.setBackground( col );
		octaveBar.setBackground( col );
		keyboard.setDarkMode( isDark );
	}
}

class MidiKeyboardPanel extends JPanel {
	private MidiEventDialog eventDialog;
	public void setEventDialog(MidiEventDialog eventDialog) {
		this.eventDialog = eventDialog;
	}
	JButton sendEventButton;
	JPanel keyboardChordPanel;
	JPanel keyboardSouthPanel;
	KeySignatureSelecter keySelecter;
	PianoKeyboardPanel keyboardCenterPanel;
	MidiChannelComboSelecter midiChannelCombobox;
	MidiChannelButtonSelecter midiChannelButtons;
	VelocitySelecter velocitySelecter;

	private static final Insets ZERO_INSETS = new Insets(0,0,0,0);

	public MidiKeyboardPanel(ChordMatrix chordMatrix) {
		keyboardCenterPanel = new PianoKeyboardPanel();
		keyboardCenterPanel.keyboard.chordMatrix = chordMatrix;
		keyboardCenterPanel.keyboard.chordDisplay =
			new ChordDisplay(
				"MIDI Keyboard", chordMatrix, keyboardCenterPanel.keyboard
			);
		//
		setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );
		add(keyboardChordPanel = new JPanel() {
			{
				setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
				add( Box.createHorizontalStrut(5) );
				add(velocitySelecter = new VelocitySelecter(
					keyboardCenterPanel.keyboard.velocityModel)
				);
				add(keySelecter = new KeySignatureSelecter(false));
				add( keyboardCenterPanel.keyboard.chordDisplay );
				add( Box.createHorizontalStrut(5) );
			}
		});
		add(keyboardCenterPanel);
		add(Box.createVerticalStrut(5));
		add(keyboardSouthPanel = new JPanel() {{
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			add(midiChannelCombobox = new MidiChannelComboSelecter(
				"MIDI Channel", keyboardCenterPanel.keyboard.midiChComboboxModel
			));
			add(midiChannelButtons = new MidiChannelButtonSelecter(
				keyboardCenterPanel.keyboard
			));
			add(sendEventButton = new JButton(new AbstractAction() {
				{ putValue(NAME,"Send MIDI event"); }
				@Override
				public void actionPerformed(ActionEvent e) {
					eventDialog.openMessageForm(
						"Send MIDI event",
						new AbstractAction() {
							{ putValue(NAME,"Send"); }
							@Override
							public void actionPerformed(ActionEvent e) {
								VirtualMidiDevice vmd = keyboardCenterPanel.keyboard.midiDevice;
								MidiMessage msg = eventDialog.midiMessageForm.getMessage();
								vmd.sendMidiMessage(msg);
							}
						},
						keyboardCenterPanel.keyboard.midiChComboboxModel.getSelectedChannel()
					);
				}
			}) {
				{ setMargin(ZERO_INSETS); }
			});
		}});
	}

	public void setDarkMode(boolean isDark) {
		Color col = isDark ? Color.black : null;
		setBackground(col);
		keyboardCenterPanel.setDarkMode(isDark);
		keyboardChordPanel.setBackground(col);
		keyboardSouthPanel.setBackground(col);
		midiChannelButtons.setBackground(col);
		midiChannelCombobox.setBackground(col);
		midiChannelCombobox.comboBox.setBackground(col);
		keySelecter.setBackground(col);
		keySelecter.keysigCombobox.setBackground(col);
		velocitySelecter.setBackground(col);
		keyboardCenterPanel.keyboard.chordDisplay.setDarkMode(isDark);
		sendEventButton.setBackground(col);
	}

}
