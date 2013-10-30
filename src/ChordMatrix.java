
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.EventListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

interface ChordMatrixListener extends EventListener {
	void chordChanged();
	void keySignatureChanged();
}
/**
 * MIDI Chord Helper 用のコードボタンマトリクス
 *
 * @author
 *	Copyright (C) 2004-2013 Akiyoshi Kamide
 *	http://www.yk.rim.or.jp/~kamide/music/chordhelper/
 */
public class ChordMatrix extends JPanel
	implements MouseListener, KeyListener, MouseMotionListener, MouseWheelListener
{
	/**
	 * 列数
	 */
	public static final int	N_COLUMNS = Music.SEMITONES_PER_OCTAVE * 2 + 1;
	/**
	 * 行数
	 */
	public static final int	CHORD_BUTTON_ROWS = 3;
	/**
	 * 調号ボタン
	 */
	public Co5Label keysigLabels[] = new Co5Label[ N_COLUMNS ];
	/**
	 * コードボタン
	 */
	public ChordLabel chordLabels[] = new ChordLabel[N_COLUMNS * CHORD_BUTTON_ROWS];
	/**
	 * コードボタンの下のコード表示部
	 */
	public ChordDisplay chordDisplay = new ChordDisplay("Chord Pad", this, null);

	private class ChordLabelSelection {
		ChordLabel chordLabel;
		int bitIndex;
		boolean isSus4;
		public ChordLabelSelection(ChordLabel chordLabel, int bitIndex) {
			this.chordLabel = chordLabel;
			this.bitIndex = bitIndex;
			this.isSus4 = chordLabel.isSus4;
		}
		public void setCheckBit(boolean isOn) {
			chordLabel.setCheckBit(isOn, bitIndex);
		}
		public boolean setBassCheckBit(boolean isOn) {
			if( bitIndex == 0 && ! isSus4 ) {
				chordLabel.setCheckBit(isOn, 6);
				return true;
			}
			return false;
		}
	}
	private class ChordLabelSelections {
		int weight = 0;
		int bass_weight = 0;
		boolean is_active = false;
		boolean is_bass_active = false;
		private ChordLabelSelection acls[];
		public ChordLabelSelections(ArrayList<ChordLabelSelection> al) {
			acls = al.toArray(new ChordLabelSelection[al.size()]);
		}
		void addWeight(int weight_diff) {
			if( (weight += weight_diff) < 0 ) weight = 0;
			if( (weight > 0) != is_active ) {
				is_active = !is_active;
				for( ChordLabelSelection cls : acls ) {
					cls.setCheckBit(is_active);
				}
			}
		}
		void addBassWeight(int weight_diff) {
			if( (bass_weight += weight_diff) < 0 ) bass_weight = 0;
			if( (bass_weight > 0) != is_bass_active ) {
				is_bass_active = !is_bass_active;
				for( ChordLabelSelection cls : acls ) {
					if( ! cls.setBassCheckBit(is_bass_active) ) {
						// No more root major/minor
						break;
					}
				}
			}
			addWeight(weight_diff);
		}
		void clearWeight() {
			weight = bass_weight = 0;
			is_active = is_bass_active = false;
			for( ChordLabelSelection cls : acls ) {
				cls.setCheckBit(false);
				cls.setBassCheckBit(false);
			}
		}
	}
	ChordLabelSelections chordLabelSelections[] =
		new ChordLabelSelections[Music.SEMITONES_PER_OCTAVE];
	/**
	 * 発音中のノート表示をクリアします。
	 */
	public void clearIndicators() {
		for( int i=0; i<chordLabelSelections.length; i++ ) {
			chordLabelSelections[i].clearWeight();
		}
		repaint();
	}
	/**
	 * MIDIのノートイベント（ON/OFF）を受け取ります。
	 * @param isNoteOn ONのときtrue
	 * @param noteNumber ノート番号
	 */
	public void note(boolean isNoteOn, int noteNumber) {
		int weightDiff = (isNoteOn ? 1 : -1);
		ChordLabelSelections cls = chordLabelSelections[Music.mod12(noteNumber)];
		if( noteNumber < 49 )
			cls.addBassWeight(weightDiff);
		else
			cls.addWeight(weightDiff);
	}

	/**
	 * 調号ボタン
	 */
	class Co5Label extends JLabel {
		public boolean isSelected = false;
		public int co5Value = 0;
		private Color indicatorColor;
		public Co5Label(int v) {
			Music.Key key = new Music.Key(co5Value = v);
			setOpaque(true);
			setBackground(false);
			setForeground( currentColorset.foregrounds[0] );
			setHorizontalAlignment( JLabel.CENTER );
			String tip = "Key signature: ";
			if( v != key.toCo5() ) {
				tip += "out of range" ;
			}
			else {
				tip += key.signatureDescription() + " " +
					key.toStringIn(Music.SymbolLanguage.IN_JAPANESE);
				if( v == 0 ) {
					setIcon(new ButtonIcon(ButtonIcon.NATURAL_ICON));
				}
				else {
					setFont( getFont().deriveFont(Font.PLAIN) );
					setText( key.signature() );
				}
			}
			setToolTipText(tip);
		}
		public void paint(Graphics g) {
			super.paint(g);
			Dimension d = getSize();
			if( ChordMatrix.this.isFocusOwner() && isSelected ) {
				g.setColor( currentColorset.focus[1] );
				g.drawRect( 0, 0, d.width-1, d.height-1 );
			}
			if( !isSelected || !isPlaying || currentBeat+1 == timesigUpper ) {
				return;
			}
			if( currentBeat == 0 ) {
				g.setColor( indicatorColor );
				g.drawRect( 2, 2, d.width-5, d.height-5 );
				g.setColor( isDark ? indicatorColor.darker() : indicatorColor.brighter() );
				g.drawRect( 0, 0, d.width-1, d.height-1 );
				return;
			}
			Color color = currentColorset.indicators[0];
			g.setColor( color );
			if( currentBeat == 1 ) {
				//
				// ||__ii
				g.drawLine( 2, d.height-3, d.width-3, d.height-3 );
				g.drawLine( d.width-3, d.height*3/4, d.width-3, d.height-3 );
				g.drawLine( 2, 2, 2, d.height-3 );
				g.setColor( isDark ? color.darker() : color.brighter() );
				g.drawLine( 0, d.height-1, d.width-1, d.height-1 );
				g.drawLine( d.width-1, d.height*3/4, d.width-1, d.height-1 );
				g.drawLine( 0, 0, 0, d.height-1 );
			}
			else {
				//
				// ii__
				//
				int vertical_top = (d.height-1) * (currentBeat-1) / (timesigUpper-2) ;
				g.drawLine( 2, vertical_top == 0 ? 2 : vertical_top, 2, d.height-3 );
				g.setColor( isDark ? color.darker() : color.brighter() );
				g.drawLine( 0, vertical_top, 0, d.height-1 );
			}
		}
		public void setBackground(boolean isActive) {
			super.setBackground(currentColorset.backgrounds[isActive?2:0]);
			setIndicatorColor();
			setOpaque(true);
		}
		public void setSelection(boolean isSelected) {
			this.isSelected = isSelected;
			setSelection();
		}
		public void setSelection() {
			setForeground(currentColorset.foregrounds[isSelected?1:0]);
		}
		public void setIndicatorColor() {
			if( co5Value < 0 ) {
				indicatorColor = currentColorset.indicators[2];
			}
			else if( co5Value > 0 ) {
				indicatorColor = currentColorset.indicators[1];
			}
			else {
				indicatorColor = currentColorset.foregrounds[1];
			}
		}
	}
	/**
	 * コードボタン
	 */
	class ChordLabel extends JLabel {
		public byte check_bits = 0;
		public int co5_value;
		public boolean is_minor;
		public boolean isSus4;
		public boolean is_selected = false;
		public Music.Chord chord;

		private boolean in_active_zone = true;
		private Font bold_font, plain_font;
		private int indicator_color_indices[] = new int[5];
		private byte indicator_bits = 0;

		public ChordLabel( Music.Chord chord ) {
			this.chord = chord;
			is_minor = chord.isMinor();
			isSus4 = chord.isSus4();
			co5_value = chord.rootNoteSymbol().toCo5();
			if( is_minor ) co5_value -= 3;
			String label_string = ( isSus4 ? chord.symbolSuffix() : chord.toString() );
			if( is_minor && label_string.length() > 3 ) {
				float small_point_size = getFont().getSize2D() - 2;
				bold_font = getFont().deriveFont(Font.BOLD, small_point_size);
				plain_font = getFont().deriveFont(Font.PLAIN, small_point_size);
			}
			else {
				bold_font = getFont().deriveFont(Font.BOLD);
				plain_font = getFont().deriveFont(Font.PLAIN);
			}
			setOpaque(true);
			setBackground(0);
			setForeground( currentColorset.foregrounds[0] );
			setBold(false);
			setHorizontalAlignment( JLabel.CENTER );
			setText( label_string );
			setToolTipText( "Chord: " + chord.toName() );
		}
		public void paint(Graphics g) {
			super.paint(g);
			Dimension d = getSize();
			Graphics2D g2 = (Graphics2D) g;
			Color color = null;

			if( ! in_active_zone ) {
				g2.setColor( Color.gray );
			}

			if( (indicator_bits & 32) != 0 ) {
				//
				// Draw square  []  with 3rd/sus4th note color
				//
				if( in_active_zone ) {
					color = currentColorset.indicators[indicator_color_indices[1]];
					g2.setColor( color );
				}
				g2.drawRect( 0, 0, d.width-1, d.height-1 );
				g2.drawRect( 2, 2, d.width-5, d.height-5 );
			}
			if( (indicator_bits & 1) != 0 ) {
				//
				// Draw  ||__  with root note color
				//
				if( in_active_zone ) {
					color = currentColorset.indicators[indicator_color_indices[0]];
					g2.setColor( color );
				}
				g2.drawLine( 0, 0, 0, d.height-1 );
				g2.drawLine( 2, 2, 2, d.height-3 );
			}
			if( (indicator_bits & 64) != 0 ) {
				// Draw bass mark with root note color
				//
				if( in_active_zone ) {
					color = currentColorset.indicators[indicator_color_indices[0]];
					g2.setColor( color );
				}
				g2.fillRect( 6, d.height-7, d.width-12, 2 );
			}
			if( (indicator_bits & 4) != 0 ) {
				//
				// Draw short  __ii  with parfect 5th color
				//
				if( in_active_zone ) {
					color = currentColorset.indicators[indicator_color_indices[2]];
					g2.setColor( color );
				}
				g2.drawLine( d.width-1, d.height*3/4, d.width-1, d.height-1 );
				g2.drawLine( d.width-3, d.height*3/4, d.width-3, d.height-3 );
			}
			if( (indicator_bits & 2) != 0 ) {
				//
				// Draw  __  with 3rd note color
				//
				if( in_active_zone ) {
					color = currentColorset.indicators[indicator_color_indices[1]];
					g2.setColor( color );
				}
				g2.drawLine( 0, d.height-1, d.width-1, d.height-1 );
				g2.drawLine( 2, d.height-3, d.width-3, d.height-3 );
			}
			if( (indicator_bits & 8) != 0 ) {
				//
				// Draw circle with diminished 5th color
				//
				if( in_active_zone ) {
					g2.setColor( currentColorset.indicators[indicator_color_indices[3]] );
				}
				g2.drawOval( 1, 1, d.width-2, d.height-2 );
			}
			if( (indicator_bits & 16) != 0 ) {
				//
				// Draw + with augument 5th color
				//
				if( in_active_zone ) {
					g2.setColor( currentColorset.indicators[indicator_color_indices[4]] );
				}
				g2.drawLine( 1, 3, d.width-3, 3 );
				g2.drawLine( 1, 4, d.width-3, 4 );
				g2.drawLine( d.width/2-1, 0, d.width/2-1, 7 );
				g2.drawLine( d.width/2, 0, d.width/2, 7 );
			}
		}
		public void clearCheckBit() {
			check_bits = indicator_bits = 0;
		}
		public void setCheckBit( boolean is_on, int bit_index ) {
			//
			// Check bits: x6x43210
			//   6:BassRoot
			//   4:Augumented5th, 3:Diminished5th, 2:Parfect5th,
			//   1:Major3rd/minor3rd/sus4th, 0:Root
			//
			byte mask = ((byte)(1<<bit_index));
			byte old_check_bits = check_bits;
			if( is_on ) {
				check_bits |= mask;
			}
			else {
				check_bits &= ~mask;
			}
			if( old_check_bits == check_bits ) {
				// No bits changed
				return;
			}
			// Indicator bits: x6543210	6:Bass||_  5:[]  4:+  3:O  2:_ii  1:__  0:||_
			//
			byte indicator_bits = 0;
			if( (check_bits & 1) != 0 ) {
				if( (check_bits & 7) == 7 ) { // All triad notes appared
					//
					// Draw square
					indicator_bits |= 0x20;
					//
					// Draw different-colored vertical lines
					if( indicator_color_indices[0] != indicator_color_indices[1] ) {
						indicator_bits |= 1;
					}
					if( indicator_color_indices[2] != indicator_color_indices[1] ) {
						indicator_bits |= 4;
					}
				}
				else if( !isSus4 ) {
					//
					// Draw vertical lines  || ii
					indicator_bits |= 5;
					//
					if( (check_bits & 2) != 0 && (!is_minor || (check_bits & 0x18) != 0) ) {
						//
						// Draw horizontal bottom lines __
						indicator_bits |= 2;
					}
				}
				if( !isSus4 ) {
					if( is_minor || (check_bits & 2) != 0 ) {
						indicator_bits |= (byte)(check_bits & 0x18);  // Copy bit 3 and bit 4
					}
					if( (check_bits & 0x40) != 0 ) {
						indicator_bits |= 0x40; // Bass
					}
				}
			}
			if( this.indicator_bits == indicator_bits ) {
				// No shapes changed
				return;
			}
			this.indicator_bits = indicator_bits;
			repaint();
		}
		public void setBackground(int i) {
			switch( i ) {
			case  0:
			case  1:
			case  2:
			case  3:
				super.setBackground(currentColorset.backgrounds[i]);
				setOpaque(true);
				break;
			default: return;
			}
		}
		public void setSelection(boolean is_selected) {
			this.is_selected = is_selected;
			setSelection();
		}
		public void setSelection() {
			setForeground(currentColorset.foregrounds[this.is_selected?1:0]);
		}
		public void setBold(boolean is_bold) {
			setFont( is_bold ? bold_font : plain_font );
		}
		public void keyChanged() {
			int co5_key = capoKey.toCo5();
			int co5_offset = co5_value - co5_key;
			in_active_zone = (co5_offset <= 6 && co5_offset >= -6) ;
			int root_note = chord.rootNoteSymbol().toNoteNumber();
			//
			// Reconstruct color index
			//
			// Root
			indicator_color_indices[0] = Music.isOnScale(
				root_note, co5_key
			) ? 0 : co5_offset > 0 ? 1 : 2;
			//
			// 3rd / sus4
			indicator_color_indices[1] = Music.isOnScale(
				root_note+(is_minor?3:isSus4?5:4), co5_key
			) ? 0 : co5_offset > 0 ? 1 : 2;
			//
			// P5th
			indicator_color_indices[2] = Music.isOnScale(
				root_note+7, co5_key
			) ? 0 : co5_offset > 0 ? 1 : 2;
			//
			// dim5th
			indicator_color_indices[3] = Music.isOnScale(
				root_note+6, co5_key
			) ? 0 : co5_offset > 4 ? 1 : 2;
			//
			// aug5th
			indicator_color_indices[4] = Music.isOnScale(
				root_note+8, co5_key
			) ? 0 : co5_offset > -3 ? 1 : 2;
		}
	}

	/**
	 * 色セット（ダークモード切替対応）
	 */
	class ColorSet {
		Color[] focus = new Color[2];	// 0:lost 1:gained
		Color[] foregrounds = new Color[2];	// 0:unselected 1:selected
		Color[] backgrounds = new Color[4]; // 0:remote 1:left 2:local 3:right
		Color[] indicators = new Color[3];	// 0:natural 1:sharp 2:flat
	}
	ColorSet normalModeColorset = new ColorSet() {
		{
			foregrounds[0] = null;
			foregrounds[1] = new Color(0xFF,0x3F,0x3F);
			backgrounds[0] = new Color(0xCF,0xFF,0xCF);
			backgrounds[1] = new Color(0x9F,0xFF,0xFF);
			backgrounds[2] = new Color(0xFF,0xCF,0xCF);
			backgrounds[3] = new Color(0xFF,0xFF,0x9F);
			indicators[0] = new Color(0xFF,0x3F,0x3F);
			indicators[1] = new Color(0xCF,0x6F,0x00);
			indicators[2] = new Color(0x3F,0x3F,0xFF);
			focus[0] = null;
			focus[1] = getBackground().darker();
		}
	};
	ColorSet darkModeColorset = new ColorSet() {
		{
			foregrounds[0] = Color.gray.darker();
			foregrounds[1] = Color.pink.brighter();
			backgrounds[0] = Color.black;
			backgrounds[1] = new Color(0x00,0x18,0x18);
			backgrounds[2] = new Color(0x20,0x00,0x00);
			backgrounds[3] = new Color(0x18,0x18,0x00);
			indicators[0] = Color.pink;
			indicators[1] = Color.yellow;
			indicators[2] = Color.cyan;
			focus[0] = Color.black;
			focus[1] = getForeground().brighter();
		}
	};
	ColorSet currentColorset = normalModeColorset;

	/**
	 * カポ値選択コンボボックスのデータモデル
	 * （コードボタン側とコードダイアグラム側の両方から参照される）
	 */
	ComboBoxModel<Integer> capoValueModel =
		new DefaultComboBoxModel<Integer>() {
			{
				for( int i=1; i<=Music.SEMITONES_PER_OCTAVE-1; i++ )
					addElement(i);
			}
		};
	/**
	 * カポ値選択コンボボックス（コードボタン側ビュー）
	 */
	CapoSelecterView capoSelecter = new CapoSelecterView(capoValueModel) {
		private void capoChanged() {
			ChordMatrix.this.capoChanged(getCapo());
		}
		{
			checkbox.addItemListener(
				new ItemListener() {
					public void itemStateChanged(ItemEvent e) {capoChanged();}
				}
			);
			valueSelecter.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {capoChanged();}
				}
			);
		}
	};

	public ChordMatrix() {
		int i, v;
		Dimension buttonSize = new Dimension(28,26);
		//
		// Make key-signature labels and chord labels
		Co5Label l;
		for (i=0, v= -Music.SEMITONES_PER_OCTAVE; i<N_COLUMNS; i++, v++) {
			l = new Co5Label(v);
			l.addMouseListener(this);
			l.addMouseMotionListener(this);
			add( keysigLabels[i] = l );
			l.setPreferredSize(buttonSize);
		}
		int row;
		for (i=0; i < N_COLUMNS * CHORD_BUTTON_ROWS; i++) {
			row = i / N_COLUMNS;
			v = i - (N_COLUMNS * row) - 12;
			Music.Chord chord = new Music.Chord(
				new Music.NoteSymbol(row==2 ? v+3 : v)
			);
			if( row==0 ) chord.setSus4();
			else if( row==2 ) chord.setMinorThird();
			ChordLabel cl = new ChordLabel(chord);
			cl.addMouseListener(this);
			cl.addMouseMotionListener(this);
			cl.addMouseWheelListener(this);
			add(chordLabels[i] = cl);
			cl.setPreferredSize(buttonSize);
		}
		setFocusable(true);
		setOpaque(true);
		addKeyListener(this);
		addFocusListener(new FocusListener() {
			public void focusGained(FocusEvent e) {
				repaint();
			}
			public void focusLost(FocusEvent e) {
				selectedChord = selectedChordCapo = null;
				fireChordChanged();
				repaint();
			}
		});
		setLayout(new GridLayout( 4, N_COLUMNS, 2, 2 ));
		setKeySignature( new Music.Key() );
		//
		// Make chord label selections index
		//
		int noteIndex;
		ArrayList<ChordLabelSelection> al;
		Music.Chord chord;
		for( int note_no=0; note_no<chordLabelSelections.length; note_no++ ) {
			al = new ArrayList<ChordLabelSelection>();
			//
			// Root major/minor chords
			for( ChordLabel cl : chordLabels ) {
				if( ! cl.isSus4 && cl.chord.indexOf(note_no) == 0 ) {
					al.add(new ChordLabelSelection( cl, 0 )); // Root
				}
			}
			// Root sus4 chords
			for( ChordLabel cl : chordLabels ) {
				if( cl.isSus4 && cl.chord.indexOf(note_no) == 0 ) {
					al.add(new ChordLabelSelection( cl, 0 )); // Root
				}
			}
			// 3rd,sus4th,5th included chords
			for( ChordLabel cl : chordLabels ) {
				noteIndex = cl.chord.indexOf(note_no);
				if( noteIndex == 1 || noteIndex == 2 ) {
					al.add(new ChordLabelSelection( cl, noteIndex )); // 3rd,sus4,P5
				}
			}
			// Diminished chords (major/minor chord button only)
			for( ChordLabel cl : chordLabels ) {
				if( cl.isSus4 ) continue;
				(chord = cl.chord.clone()).setFlattedFifth();
				if( chord.indexOf(note_no) == 2 ) {
					al.add(new ChordLabelSelection( cl, 3 ));
				}
			}
			// Augumented chords (major chord button only)
			for( ChordLabel cl : chordLabels ) {
				if( cl.isSus4 || cl.is_minor ) continue;
				(chord = cl.chord.clone()).setSharpedFifth();
				if( chord.indexOf(note_no) == 2 ) {
					al.add(new ChordLabelSelection( cl, 4 ));
				}
			}
			chordLabelSelections[note_no] = new ChordLabelSelections(al);
		}
	}
	//
	// MouseListener
	public void mousePressed(MouseEvent e) {
		Component obj = e.getComponent();
		if( obj instanceof ChordLabel ) {
			ChordLabel cl = (ChordLabel)obj;
			Music.Chord chord = cl.chord.clone();
			if( (e.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0 ) {
				if( e.isShiftDown() ) chord.setMajorSeventh();
				else chord.setSeventh();
			}
			else if( e.isShiftDown() ) chord.setSixth();

			if( e.isControlDown() ) chord.setNinth();
			else chord.clearNinth();

			if( e.isAltDown() ) {
				if( cl.isSus4 ) {
					chord.setMajorThird(); // To cancel sus4
					chord.setSharpedFifth();
				}
				else chord.setFlattedFifth();
			}
			if( selectedChordLabel != null ) {
				selectedChordLabel.setSelection(false);
			}
			(selectedChordLabel = cl).setSelection(true);
			setSelectedChord(chord);
		}
		else if( obj instanceof Co5Label ) {
			int v = ((Co5Label)obj).co5Value;
			if( (e.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0 ) {
				setKeySignature( new Music.Key(Music.oppositeCo5(v)) );
			}
			else if ( v == key.toCo5() ) {
				//
				// Cancel selected chord
				//
				setSelectedChord( (Music.Chord)null );
			}
			else {
				// Change key
				setKeySignature( new Music.Key(v) );
			}
		}
		requestFocusInWindow();
		repaint();
	}
	public void mouseReleased(MouseEvent e) {
		destinationChordLabel = null;
	}
	public void mouseEntered(MouseEvent e) { }
	public void mouseExited(MouseEvent e) { }
	public void mouseClicked(MouseEvent e) { }
	//
	// MouseMotionListener
	public void mouseDragged(MouseEvent e) {
		Component obj = e.getComponent();
		if( obj instanceof ChordLabel ) {
			ChordLabel l_src = (ChordLabel)obj;
			Component obj2 = this.getComponentAt(
				l_src.getX() + e.getX(),
				l_src.getY() + e.getY()
			);
			if( obj2 == this ) {
				//
				// Entered gap between chord buttons - do nothing
				//
				return;
			}
			ChordLabel l_dst =
				( (obj2 instanceof ChordLabel ) ? (ChordLabel)obj2 : null );
			if( l_dst == l_src ) {
				//
				// Returned to original chord button
				//
				destinationChordLabel = null;
				return;
			}
			if( destinationChordLabel != null ) {
				//
				// Already touched another chord button
				//
				return;
			}
			Music.Chord chord = l_src.chord.clone();
			if( l_src.is_minor ) {
				if( l_dst == null ) { // Out of chord buttons
					// mM7
					chord.setMajorSeventh();
				}
				else if( l_src.co5_value < l_dst.co5_value ) { // Right
					// m6
					chord.setSixth();
				}
				else { // Left or up from minor to major
					// m7
					chord.setSeventh();
				}
			}
			else if( l_src.isSus4 ) {
				if( l_dst == null ) { // Out of chord buttons
					return;
				}
				else if( ! l_dst.isSus4 ) { // Down from sus4 to major
					chord.setMajorThird();
				}
				else if( l_src.co5_value < l_dst.co5_value ) { // Right
					chord.setNinth();
				}
				else { // Left
					// 7sus4
					chord.setSeventh();
				}
			}
			else {
				if( l_dst == null ) { // Out of chord buttons
					return;
				}
				else if( l_dst.isSus4 ) { // Up from major to sus4
					chord.setNinth();
				}
				else if( l_src.co5_value < l_dst.co5_value ) { // Right
					// M7
					chord.setMajorSeventh();
				}
				else if( l_dst.is_minor ) { // Down from major to minor
					// 6
					chord.setSixth();
				}
				else { // Left
					// 7
					chord.setSeventh();
				}
			}
			if( chord.hasNinth() || (l_src.isSus4 && (l_dst == null || ! l_dst.isSus4) ) ) {
				if( (e.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0 ) {
					if( e.isShiftDown() ) {
						chord.setMajorSeventh();
					}
					else {
						chord.setSeventh();
					}
				}
				else if( e.isShiftDown() ) {
					chord.setSixth();
				}
			}
			else {
				if( e.isControlDown() ) chord.setNinth(); else chord.clearNinth();
			}
			if( e.isAltDown() ) {
				if( l_src.isSus4 ) {
					chord.setMajorThird();
					chord.setSharpedFifth();
				}
				else {
					chord.setFlattedFifth();
				}
			}
			setSelectedChord(chord);
			destinationChordLabel = (l_dst == null ? l_src : l_dst ) ;
		}
		else if( obj instanceof Co5Label ) {
			Co5Label l_src = (Co5Label)obj;
			Component obj2 = this.getComponentAt(
				l_src.getX() + e.getX(),
				l_src.getY() + e.getY()
			);
			if( !(obj2 instanceof Co5Label) ) {
				return;
			}
			Co5Label l_dst = (Co5Label)obj2;
			int v = l_dst.co5Value;
			if( (e.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0 ) {
				setKeySignature( new Music.Key(Music.oppositeCo5(v)) );
			}
			else {
				setKeySignature( new Music.Key(v) );
			}
			repaint();
		}
	}
	public void mouseMoved(MouseEvent e) { }
	public void mouseWheelMoved(MouseWheelEvent e) {
		if( selectedChord != null ) {
			if( e.getWheelRotation() > 0 ) { // Wheel moved down
				if( --selectedNoteIndex < 0 ) {
					selectedNoteIndex = selectedChord.numberOfNotes() - 1;
				}
			}
			else { // Wheel moved up
				if( ++selectedNoteIndex >= selectedChord.numberOfNotes() ) {
					selectedNoteIndex = 0;
				}
			}
			fireChordChanged();
		}
	}
	private int pcKeyNextShift7 = Music.Chord.ROOT;
	public void keyPressed(KeyEvent e) {
		int i = -1, i_col = -1, i_row = 1;
		boolean shift_pressed = false; // True if Shift-key pressed or CapsLocked
		char keyChar = e.getKeyChar();
		int keyCode = e.getKeyCode();
		ChordLabel cl = null;
		Music.Chord chord = null;
		int key_co5 = key.toCo5();
		// System.out.println( keyChar + " Pressed on chord matrix" );
		//
		if( (i = "6 ".indexOf(keyChar)) >= 0 ) {
			selectedChord = selectedChordCapo = null;
			fireChordChanged();
			pcKeyNextShift7 = Music.Chord.ROOT;
			return;
		}
		else if( (i = "asdfghjkl;:]".indexOf(keyChar)) >= 0 ) {
			i_col = i + key_co5 + 7;
		}
		else if( (i = "ASDFGHJKL+*}".indexOf(keyChar)) >= 0 ) {
			i_col = i + key_co5 + 7;
			shift_pressed = true;
		}
		else if( (i = "zxcvbnm,./\\".indexOf(keyChar)) >=0 ) {
			i_col = i + key_co5 + 7;
			i_row = 2;
		}
		else if( (i = "ZXCVBNM<>?_".indexOf(keyChar)) >=0 ) {
			i_col = i + key_co5 + 7;
			i_row = 2;
			shift_pressed = true;
		}
		else if( (i = "qwertyuiop@[".indexOf(keyChar)) >= 0 ) {
			i_col = i + key_co5 + 7;
			i_row = 0;
		}
		else if( (i = "QWERTYUIOP`{".indexOf(keyChar)) >= 0 ) {
			i_col = i + key_co5 + 7;
			i_row = 0;
			shift_pressed = true;
		}
		else if( keyChar == '5' ) {
			pcKeyNextShift7 = Music.Chord.MAJOR_SEVENTH; return;
		}
		else if( keyChar == '7' ) {
			pcKeyNextShift7 = Music.Chord.SEVENTH; return;
		}
		// Shift current key-signature
		else if( keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_KP_LEFT ) {
			// Add a flat
			setKeySignature( new Music.Key(key_co5-1) );
			return;
		}
		else if( keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_KP_RIGHT ) {
			// Add a sharp
			setKeySignature( new Music.Key(key_co5+1) );
			return;
		}
		else if( keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_KP_DOWN ) {
			// Semitone down
			Music.Key key = new Music.Key(key_co5);
			key.transpose(-1);
			setKeySignature(key);
			return;
		}
		else if( keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_KP_UP ) {
			// Semitone up
			Music.Key key = new Music.Key(key_co5);
			key.transpose(1);
			setKeySignature(key);
			return;
		}
		if( i < 0 ) // No key char found
			return;
		if( i_col < 0 ) i_col += 12; else if( i_col > N_COLUMNS ) i_col -= 12;
		cl = chordLabels[i_col + N_COLUMNS * i_row];
		chord = cl.chord.clone();
		if( shift_pressed )
			chord.setSeventh();
		else
			chord.offsets[Music.Chord.SEVENTH_OFFSET] = pcKeyNextShift7; // specify by previous key
		if( e.isAltDown() ) {
			if( cl.isSus4 ) {
				chord.setMajorThird(); // To cancel sus4
				chord.setSharpedFifth();
			}
			else chord.setFlattedFifth();
		}
		if( e.isControlDown() ) { // Cannot use for ninth ?
			chord.setNinth();
		}
		if( selectedChordLabel != null ) clear();
		(selectedChordLabel = cl).setSelection(true);
		setSelectedChord(chord);
		pcKeyNextShift7 = Music.Chord.ROOT;
		return;
	}
	public void keyReleased(KeyEvent e) { }
	public void keyTyped(KeyEvent e) { }

	public void addChordMatrixListener(ChordMatrixListener l) {
		listenerList.add(ChordMatrixListener.class, l);
	}
	public void removeChordMatrixListener(ChordMatrixListener l) {
		listenerList.remove(ChordMatrixListener.class, l);
	}
	protected void fireChordChanged() {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==ChordMatrixListener.class) {
				((ChordMatrixListener)listeners[i+1]).chordChanged();
			}
		}
		if( selectedChord == null ) clearIndicators();
	}
	protected void fireKeySignatureChanged() {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==ChordMatrixListener.class) {
				((ChordMatrixListener)listeners[i+1]).keySignatureChanged();
			}
		}
	}
	private Music.Key key = null;
	private Music.Key capoKey = null;
	public Music.Key getKeySignature() { return key; }
	public Music.Key getKeySignatureCapo() { return capoKey; }
	public void setKeySignature( Music.Key key ) {
		if( key == null || this.key != null && key.equals(this.key) )
			return;
		int i;
		// Clear old value
		if( this.key == null ) {
			for( i = 0; i < keysigLabels.length; i++ ) {
				keysigLabels[i].setBackground(false);
			}
		}
		else {
			keysigLabels[this.key.toCo5() + 12].setSelection(false);
			for( i = Music.mod12(this.key.toCo5()); i < N_COLUMNS; i+=12 ) {
				keysigLabels[i].setBackground(false);
			}
		}
		// Set new value
		keysigLabels[i = key.toCo5() + 12].setSelection(true);
		for( i = Music.mod12(key.toCo5()); i < N_COLUMNS; i+=12 ) {
			keysigLabels[i].setBackground(true);
		}
		// Change chord-label's color & font
		int i_color, old_i_color;
		for( ChordLabel cl : chordLabels ) {
			i_color = ((cl.co5_value - key.toCo5() + 31)/3) & 3;
			if( this.key != null ) {
				old_i_color = ((cl.co5_value - this.key.toCo5() + 31)/3) & 3;
				if( i_color != old_i_color ) {
					cl.setBackground(i_color);
				}
			}
			else cl.setBackground(i_color);
			if( !(cl.isSus4) ) {
				if( this.key != null && Music.mod12(cl.co5_value - this.key.toCo5()) == 0)
					cl.setBold(false);
				if( Music.mod12( cl.co5_value - key.toCo5() ) == 0 )
					cl.setBold(true);
			}
		}
		this.capoKey = (this.key = key).clone().transpose(capoSelecter.getCapo());
		for( ChordLabel cl : chordLabels ) cl.keyChanged();
		fireKeySignatureChanged();
	}
	private int capo = 0;
	/**
	 * カポ位置の変更処理
	 * @param newCapo 新しいカポ位置
	 */
	protected void capoChanged(int newCapo) {
		if( this.capo == newCapo )
			return;
		(this.capoKey = this.key.clone()).transpose(this.capo = newCapo);
		selectedChordCapo = (
			selectedChord == null ? null : selectedChord.clone().transpose(newCapo)
		);
		for( ChordLabel cl : chordLabels ) cl.keyChanged();
		fireKeySignatureChanged();
	}

	/**
	 * コードサフィックスのヘルプ
	 */
	public ChordGuide chordGuide = new ChordGuide(this);

	/**
	 * ドラッグ先コードボタン
	 */
	private ChordLabel	destinationChordLabel = null;
	/**
	 * ドラッグされたかどうか調べます。
	 * @return ドラッグ先コードボタンがあればtrue
	 */
	public boolean isDragged() {
		return destinationChordLabel != null ;
	}

	private boolean isDark = false;
	public void setDarkMode(boolean is_dark) {
		this.isDark = is_dark;
		currentColorset = (is_dark ? darkModeColorset : normalModeColorset);
		setBackground( currentColorset.focus[0] );
		Music.Key prev_key = key;
		key = null;
		setKeySignature(prev_key);
		for( int i=0; i < keysigLabels.length; i++ ) keysigLabels[i].setSelection();
		for( int i=0; i <  chordLabels.length; i++ ) chordLabels[i].setSelection();
		chordGuide.setDarkMode( is_dark );
		chordDisplay.setDarkMode( is_dark );
		Color col = is_dark ? Color.black : null;
		capoSelecter.setBackground( col );
		capoSelecter.valueSelecter.setBackground( col );
	}

	private boolean isPlaying = false;
	public boolean isPlaying() { return isPlaying; }
	public void setPlaying(boolean is_playing) {
		this.isPlaying = is_playing;
		repaint();
	}

	private byte currentBeat = 0;
	private byte timesigUpper = 4;
	public void setBeat(byte beat, byte tsu) {
		if( currentBeat == beat && timesigUpper == tsu )
			return;
		timesigUpper = tsu;
		currentBeat = beat;
		keysigLabels[ key.toCo5() + 12 ].repaint();
	}

	private ChordLabel	selectedChordLabel = null;
	public JComponent getSelectedButton() {
		return selectedChordLabel;
	}
	private Music.Chord	selectedChord = null;
	public Music.Chord getSelectedChord() {
		return selectedChord;
	}
	private Music.Chord	selectedChordCapo = null;
	public Music.Chord getSelectedChordCapo() {
		return selectedChordCapo;
	}
	public void setSelectedChordCapo( Music.Chord chord ) {
		setNoteIndex(-1); // Cancel arpeggio mode
		selectedChord = (chord == null ? null : chord.clone().transpose(-capo,capoKey));
		selectedChordCapo = chord;
		fireChordChanged();
	}
	public void setSelectedChord( Music.Chord chord ) {
		setNoteIndex(-1); // Cancel arpeggio mode
		selectedChord = chord;
		selectedChordCapo = (chord == null ? null : chord.clone().transpose(capo,key));
		fireChordChanged();
	}
	public void setSelectedChord( String chordSymbol ) {
		Music.Chord chord = null;
		if( chordSymbol != null && ! chordSymbol.isEmpty() ) {
			try {
				chord = new Music.Chord(chordSymbol);
			} catch( IllegalArgumentException ex ) {
				// Ignored
			}
		}
		setSelectedChord(chord);
	}

	private int selectedNoteIndex = -1;
	public int getNoteIndex() {
		return selectedChord == null || selectedNoteIndex < 0 ? -1 : selectedNoteIndex;
	}
	public void setNoteIndex(int noteIndex) {
		selectedNoteIndex = noteIndex;
	}
	public void clear() {
		if( selectedChordLabel != null ) {
			selectedChordLabel.setSelection(false);
			selectedChordLabel = null;
		}
		selectedChord = null; selectedNoteIndex = -1;
	}
}

/**
 * コードボタン用のマトリクス外のラベル
 */
class ChordButtonLabel extends JLabel {
	private ChordMatrix cm;
	public ChordButtonLabel(String txt, ChordMatrix cm) {
		super(txt,CENTER);
		this.cm = cm;
		setOpaque(true);
		setFont(getFont().deriveFont(Font.PLAIN));
		setDarkMode(false);
	}
	public void setDarkMode(boolean isDark) {
		setBackground( isDark ?
			cm.darkModeColorset.backgrounds[2] :
			cm.normalModeColorset.backgrounds[2]
		);
		setForeground( isDark ?
			cm.darkModeColorset.foregrounds[0] :
			cm.normalModeColorset.foregrounds[0]
		);
	}
}
/**
 * コードサフィックスのヘルプ
 */
class ChordGuide extends JPanel {
	private class ChordGuideLabel extends ChordButtonLabel {
		private JPopupMenu popupMenu = new JPopupMenu();
		public ChordGuideLabel(String txt, ChordMatrix cm) {
			super(txt,cm);
			addMouseListener(
				new MouseAdapter() {
					public void mousePressed(MouseEvent e) {
						popupMenu.show( e.getComponent(), 0, getHeight() );
					}
				}
			);
		}
		public void addMenu(JMenuItem menuItem) { popupMenu.add(menuItem); }
		public void addSeparator() { popupMenu.addSeparator(); }
	}
	private ChordGuideLabel guide76, guide5, guide9;
	public ChordGuide(ChordMatrix cm) {
		guide76 = new ChordGuideLabel(" 6  7  M7 ",cm) {
			{
				setToolTipText("How to add 7th, major 7th, 6th");
				addMenu(new JMenuItem("7        = <RightClick>"));
				addMenu(new JMenuItem("M7(maj7) = [Shift] <RightClick>"));
				addMenu(new JMenuItem("6        = [Shift]"));
			}
		};
		guide5 = new ChordGuideLabel(" -5 dim +5 aug ",cm){
			{
				setToolTipText("How to add -5, dim, +5, aug");
				addMenu(new JMenuItem("-5 (b5)      = [Alt]"));
				addMenu(new JMenuItem("+5 (#5/aug)  = [Alt] sus4"));
				addSeparator();
				addMenu(new JMenuItem("dim  (m-5)  = [Alt] minor"));
				addMenu(new JMenuItem("dim7 (m6-5) = [Alt] [Shift] minor"));
				addMenu(new JMenuItem("m7-5 = [Alt] minor <RightClick>"));
				addMenu(new JMenuItem("aug7 (7+5)  = [Alt] sus4 <RightClick>"));
			}
		};
		guide9 = new ChordGuideLabel(" add9 ",cm) {
			{
				setToolTipText("How to add 9th");
				addMenu(new JMenuItem("add9  = [Ctrl]"));
				addSeparator();
				addMenu(new JMenuItem("9     = [Ctrl] <RightClick>"));
				addMenu(new JMenuItem("M9    = [Ctrl] [Shift] <RightClick>"));
				addMenu(new JMenuItem("69    = [Ctrl] [Shift]"));
				addMenu(new JMenuItem("dim9  = [Ctrl] [Shift] [Alt] minor"));
			}
		};
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(guide76);
		add( Box.createHorizontalStrut(2) );
		add(guide5);
		add( Box.createHorizontalStrut(2) );
		add(guide9);
	}
	public void setDarkMode(boolean is_dark) {
		setBackground( is_dark ? Color.black : null );
		guide76.setDarkMode( is_dark );
		guide5.setDarkMode( is_dark );
		guide9.setDarkMode( is_dark );
	}
}

