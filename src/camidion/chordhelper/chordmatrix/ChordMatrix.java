package camidion.chordhelper.chordmatrix;

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
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import camidion.chordhelper.ButtonIcon;
import camidion.chordhelper.ChordDisplayLabel;
import camidion.chordhelper.chorddiagram.CapoComboBoxModel;
import camidion.chordhelper.chorddiagram.CapoSelecterView;
import camidion.chordhelper.midieditor.SequenceTickIndex;
import camidion.chordhelper.music.Chord;
import camidion.chordhelper.music.Key;
import camidion.chordhelper.music.Music;
import camidion.chordhelper.music.NoteSymbol;

/**
 * MIDI Chord Helper 用のコードボタンマトリクス
 *
 * @author
 *	Copyright (C) 2004-2016 Akiyoshi Kamide
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
	public ChordDisplayLabel chordDisplay = new ChordDisplayLabel("Chord Pad", this, null);

	private static class ChordLabelSelection {
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
	private static class ChordLabelSelections {
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
	private ChordLabelSelections chordLabelSelections[] =
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
	private class Co5Label extends JLabel {
		public boolean isSelected = false;
		public int co5Value = 0;
		private Color indicatorColor;
		public Co5Label(int v) {
			Key key = new Key(co5Value = v);
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
					key.toStringIn(NoteSymbol.Language.IN_JAPANESE);
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
	private class ChordLabel extends JLabel {
		public byte checkBits = 0;
		public int co5Value;
		public boolean isMinor;
		public boolean isSus4;
		public boolean isSelected = false;
		public Chord chord;

		private boolean inActiveZone = true;
		private Font boldFont;
		private Font plainFont;
		private int indicatorColorIndices[] = new int[5];
		private byte indicatorBits = 0;

		public ChordLabel(Chord chord) {
			this.chord = chord;
			isMinor = chord.isSet(Chord.Interval.MINOR);
			isSus4 = chord.isSet(Chord.Interval.SUS4);
			co5Value = chord.rootNoteSymbol().toCo5();
			if( isMinor ) co5Value -= 3;
			String labelText = ( isSus4 ? chord.symbolSuffix() : chord.toString() );
			if( isMinor && labelText.length() > 3 ) {
				float small_point_size = getFont().getSize2D() - 2;
				boldFont = getFont().deriveFont(Font.BOLD, small_point_size);
				plainFont = getFont().deriveFont(Font.PLAIN, small_point_size);
			}
			else {
				boldFont = getFont().deriveFont(Font.BOLD);
				plainFont = getFont().deriveFont(Font.PLAIN);
			}
			setOpaque(true);
			setBackground(0);
			setForeground( currentColorset.foregrounds[0] );
			setBold(false);
			setHorizontalAlignment( JLabel.CENTER );
			setText(labelText);
			setToolTipText( "Chord: " + chord.toName() );
		}
		public void paint(Graphics g) {
			super.paint(g);
			Dimension d = getSize();
			Graphics2D g2 = (Graphics2D) g;
			Color color = null;

			if( ! inActiveZone ) {
				g2.setColor( Color.gray );
			}

			if( (indicatorBits & 32) != 0 ) {
				//
				// Draw square  []  with 3rd/sus4th note color
				//
				if( inActiveZone ) {
					color = currentColorset.indicators[indicatorColorIndices[1]];
					g2.setColor( color );
				}
				g2.drawRect( 0, 0, d.width-1, d.height-1 );
				g2.drawRect( 2, 2, d.width-5, d.height-5 );
			}
			if( (indicatorBits & 1) != 0 ) {
				//
				// Draw  ||__  with root note color
				//
				if( inActiveZone ) {
					color = currentColorset.indicators[indicatorColorIndices[0]];
					g2.setColor( color );
				}
				g2.drawLine( 0, 0, 0, d.height-1 );
				g2.drawLine( 2, 2, 2, d.height-3 );
			}
			if( (indicatorBits & 64) != 0 ) {
				// Draw bass mark with root note color
				//
				if( inActiveZone ) {
					color = currentColorset.indicators[indicatorColorIndices[0]];
					g2.setColor( color );
				}
				g2.fillRect( 6, d.height-7, d.width-12, 2 );
			}
			if( (indicatorBits & 4) != 0 ) {
				//
				// Draw short  __ii  with parfect 5th color
				//
				if( inActiveZone ) {
					color = currentColorset.indicators[indicatorColorIndices[2]];
					g2.setColor( color );
				}
				g2.drawLine( d.width-1, d.height*3/4, d.width-1, d.height-1 );
				g2.drawLine( d.width-3, d.height*3/4, d.width-3, d.height-3 );
			}
			if( (indicatorBits & 2) != 0 ) {
				//
				// Draw  __  with 3rd note color
				//
				if( inActiveZone ) {
					color = currentColorset.indicators[indicatorColorIndices[1]];
					g2.setColor( color );
				}
				g2.drawLine( 0, d.height-1, d.width-1, d.height-1 );
				g2.drawLine( 2, d.height-3, d.width-3, d.height-3 );
			}
			if( (indicatorBits & 8) != 0 ) {
				//
				// Draw circle with diminished 5th color
				//
				if( inActiveZone ) {
					g2.setColor( currentColorset.indicators[indicatorColorIndices[3]] );
				}
				g2.drawOval( 1, 1, d.width-2, d.height-2 );
			}
			if( (indicatorBits & 16) != 0 ) {
				//
				// Draw + with augument 5th color
				//
				if( inActiveZone ) {
					g2.setColor( currentColorset.indicators[indicatorColorIndices[4]] );
				}
				g2.drawLine( 1, 3, d.width-3, 3 );
				g2.drawLine( 1, 4, d.width-3, 4 );
				g2.drawLine( d.width/2-1, 0, d.width/2-1, 7 );
				g2.drawLine( d.width/2, 0, d.width/2, 7 );
			}
		}
		public void setCheckBit( boolean is_on, int bit_index ) {
			//
			// Check bits: x6x43210
			//   6:BassRoot
			//   4:Augumented5th, 3:Diminished5th, 2:Parfect5th,
			//   1:Major3rd/minor3rd/sus4th, 0:Root
			//
			byte mask = ((byte)(1<<bit_index));
			byte old_check_bits = checkBits;
			if( is_on ) {
				checkBits |= mask;
			}
			else {
				checkBits &= ~mask;
			}
			if( old_check_bits == checkBits ) {
				// No bits changed
				return;
			}
			// Indicator bits: x6543210	6:Bass||_  5:[]  4:+  3:O  2:_ii  1:__  0:||_
			//
			byte indicator_bits = 0;
			if( (checkBits & 1) != 0 ) {
				if( (checkBits & 7) == 7 ) { // All triad notes appared
					//
					// Draw square
					indicator_bits |= 0x20;
					//
					// Draw different-colored vertical lines
					if( indicatorColorIndices[0] != indicatorColorIndices[1] ) {
						indicator_bits |= 1;
					}
					if( indicatorColorIndices[2] != indicatorColorIndices[1] ) {
						indicator_bits |= 4;
					}
				}
				else if( !isSus4 ) {
					//
					// Draw vertical lines  || ii
					indicator_bits |= 5;
					//
					if( (checkBits & 2) != 0 && (!isMinor || (checkBits & 0x18) != 0) ) {
						//
						// Draw horizontal bottom lines __
						indicator_bits |= 2;
					}
				}
				if( !isSus4 ) {
					if( isMinor || (checkBits & 2) != 0 ) {
						indicator_bits |= (byte)(checkBits & 0x18);  // Copy bit 3 and bit 4
					}
					if( (checkBits & 0x40) != 0 ) {
						indicator_bits |= 0x40; // Bass
					}
				}
			}
			if( this.indicatorBits == indicator_bits ) {
				// No shapes changed
				return;
			}
			this.indicatorBits = indicator_bits;
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
			this.isSelected = is_selected;
			setSelection();
		}
		public void setSelection() {
			setForeground(currentColorset.foregrounds[this.isSelected?1:0]);
		}
		public void setBold(boolean is_bold) {
			setFont( is_bold ? boldFont : plainFont );
		}
		public void keyChanged() {
			int co5_key = capoKey.toCo5();
			int co5_offset = co5Value - co5_key;
			inActiveZone = (co5_offset <= 6 && co5_offset >= -6) ;
			int root_note = chord.rootNoteSymbol().toNoteNumber();
			//
			// Reconstruct color index
			//
			// Root
			indicatorColorIndices[0] = Music.isOnScale(
				root_note, co5_key
			) ? 0 : co5_offset > 0 ? 1 : 2;
			//
			// 3rd / sus4
			indicatorColorIndices[1] = Music.isOnScale(
				root_note+(isMinor?3:isSus4?5:4), co5_key
			) ? 0 : co5_offset > 0 ? 1 : 2;
			//
			// P5th
			indicatorColorIndices[2] = Music.isOnScale(
				root_note+7, co5_key
			) ? 0 : co5_offset > 0 ? 1 : 2;
			//
			// dim5th
			indicatorColorIndices[3] = Music.isOnScale(
				root_note+6, co5_key
			) ? 0 : co5_offset > 4 ? 1 : 2;
			//
			// aug5th
			indicatorColorIndices[4] = Music.isOnScale(
				root_note+8, co5_key
			) ? 0 : co5_offset > -3 ? 1 : 2;
		}
	}

	/**
	 * 色セット（ダークモード切替対応）
	 */
	public class ColorSet {
		Color[] focus = new Color[2];	// 0:lost 1:gained
		Color[] foregrounds = new Color[2];	// 0:unselected 1:selected
		public Color[] backgrounds = new Color[4]; // 0:remote 1:left 2:local 3:right
		Color[] indicators = new Color[3];	// 0:natural 1:sharp 2:flat
	}
	public ColorSet normalModeColorset = new ColorSet() {
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
	public ColorSet darkModeColorset = new ColorSet() {
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
	private ColorSet currentColorset = normalModeColorset;

	/**
	 * カポ値選択コンボボックス（コードボタン側ビュー）
	 */
	public CapoSelecterView capoSelecter;

	/**
	 * コードボタンマトリクスの構築
	 * @param capoValueModel カポ選択値モデル
	 */
	public ChordMatrix(CapoComboBoxModel capoValueModel) {
		capoSelecter = new CapoSelecterView(capoValueModel) {{
			checkbox.addItemListener(new ItemListener() {
				public void itemStateChanged(ItemEvent e) {capoChanged(getCapo());}
			});
			valueSelecter.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {capoChanged(getCapo());}
			});
		}};
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
			Chord chord = new Chord(
				new NoteSymbol(row==2 ? v+3 : v)
			);
			if( row==0 ) chord.set(Chord.Interval.SUS4);
			else if( row==2 ) chord.set(Chord.Interval.MINOR);
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
		setKeySignature( new Key() );
		//
		// Make chord label selections index
		//
		int noteIndex;
		ArrayList<ChordLabelSelection> al;
		Chord chord;
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
				(chord = cl.chord.clone()).set(Chord.Interval.FLAT5);
				if( chord.indexOf(note_no) == 2 ) {
					al.add(new ChordLabelSelection( cl, 3 ));
				}
			}
			// Augumented chords (major chord button only)
			for( ChordLabel cl : chordLabels ) {
				if( cl.isSus4 || cl.isMinor ) continue;
				(chord = cl.chord.clone()).set(Chord.Interval.SHARP5);
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
			Chord chord = cl.chord.clone();
			if( (e.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0 ) {
				if( e.isShiftDown() )
					chord.set(Chord.Interval.MAJOR_SEVENTH);
				else
					chord.set(Chord.Interval.SEVENTH);
			}
			else if( e.isShiftDown() )
				chord.set(Chord.Interval.SIXTH);
			if( e.isControlDown() )
				chord.set(Chord.Interval.NINTH);
			else
				chord.clear(Chord.OffsetIndex.NINTH);

			if( e.isAltDown() ) {
				if( cl.isSus4 ) {
					chord.set(Chord.Interval.MAJOR); // To cancel sus4
					chord.set(Chord.Interval.SHARP5);
				}
				else chord.set(Chord.Interval.FLAT5);
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
				setKeySignature( new Key(Music.oppositeCo5(v)) );
			}
			else if ( v == key.toCo5() ) {
				//
				// Cancel selected chord
				//
				setSelectedChord( (Chord)null );
			}
			else {
				// Change key
				setKeySignature( new Key(v) );
			}
		}
		requestFocusInWindow();
		repaint();
	}
	public void mouseReleased(MouseEvent e) { destinationChordLabel = null; }
	public void mouseEntered(MouseEvent e) { }
	public void mouseExited(MouseEvent e) { }
	public void mouseClicked(MouseEvent e) { }
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
			Chord chord = l_src.chord.clone();
			if( l_src.isMinor ) {
				if( l_dst == null ) { // Out of chord buttons
					// mM7
					chord.set(Chord.Interval.MAJOR_SEVENTH);
				}
				else if( l_src.co5Value < l_dst.co5Value ) { // Right
					// m6
					chord.set(Chord.Interval.SIXTH);
				}
				else { // Left or up from minor to major
					// m7
					chord.set(Chord.Interval.SEVENTH);
				}
			}
			else if( l_src.isSus4 ) {
				if( l_dst == null ) { // Out of chord buttons
					return;
				}
				else if( ! l_dst.isSus4 ) { // Down from sus4 to major
					chord.set(Chord.Interval.MAJOR);
				}
				else if( l_src.co5Value < l_dst.co5Value ) { // Right
					chord.set(Chord.Interval.NINTH);
				}
				else { // Left
					// 7sus4
					chord.set(Chord.Interval.SEVENTH);
				}
			}
			else {
				if( l_dst == null ) { // Out of chord buttons
					return;
				}
				else if( l_dst.isSus4 ) { // Up from major to sus4
					chord.set(Chord.Interval.NINTH);
				}
				else if( l_src.co5Value < l_dst.co5Value ) { // Right
					// M7
					chord.set(Chord.Interval.MAJOR_SEVENTH);
				}
				else if( l_dst.isMinor ) { // Down from major to minor
					// 6
					chord.set(Chord.Interval.SIXTH);
				}
				else { // Left
					// 7
					chord.set(Chord.Interval.SEVENTH);
				}
			}
			if( chord.isSet(Chord.OffsetIndex.NINTH) || (l_src.isSus4 && (l_dst == null || ! l_dst.isSus4) ) ) {
				if( (e.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0 ) {
					if( e.isShiftDown() ) {
						chord.set(Chord.Interval.MAJOR_SEVENTH);
					}
					else {
						chord.set(Chord.Interval.SEVENTH);
					}
				}
				else if( e.isShiftDown() ) {
					chord.set(Chord.Interval.SIXTH);
				}
			}
			else {
				if( e.isControlDown() )
					chord.set(Chord.Interval.NINTH);
				else
					chord.clear(Chord.OffsetIndex.NINTH);
			}
			if( e.isAltDown() ) {
				if( l_src.isSus4 ) {
					chord.set(Chord.Interval.MAJOR);
					chord.set(Chord.Interval.SHARP5);
				}
				else {
					chord.set(Chord.Interval.FLAT5);
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
				setKeySignature( new Key(Music.oppositeCo5(v)) );
			}
			else {
				setKeySignature( new Key(v) );
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
	private Chord.Interval pcKeyNextShift7;
	public void keyPressed(KeyEvent e) {
		int i = -1, i_col = -1, i_row = 1;
		boolean shiftPressed = false; // True if Shift-key pressed or CapsLocked
		char keyChar = e.getKeyChar();
		int keyCode = e.getKeyCode();
		ChordLabel cl = null;
		Chord chord = null;
		int key_co5 = key.toCo5();
		// System.out.println( keyChar + " Pressed on chord matrix" );
		//
		if( (i = "6 ".indexOf(keyChar)) >= 0 ) {
			selectedChord = selectedChordCapo = null;
			fireChordChanged();
			pcKeyNextShift7 = null;
			return;
		}
		else if( (i = "asdfghjkl;:]".indexOf(keyChar)) >= 0 ) {
			i_col = i + key_co5 + 7;
		}
		else if( (i = "ASDFGHJKL+*}".indexOf(keyChar)) >= 0 ) {
			i_col = i + key_co5 + 7;
			shiftPressed = true;
		}
		else if( (i = "zxcvbnm,./\\".indexOf(keyChar)) >=0 ) {
			i_col = i + key_co5 + 7;
			i_row = 2;
		}
		else if( (i = "ZXCVBNM<>?_".indexOf(keyChar)) >=0 ) {
			i_col = i + key_co5 + 7;
			i_row = 2;
			shiftPressed = true;
		}
		else if( (i = "qwertyuiop@[".indexOf(keyChar)) >= 0 ) {
			i_col = i + key_co5 + 7;
			i_row = 0;
		}
		else if( (i = "QWERTYUIOP`{".indexOf(keyChar)) >= 0 ) {
			i_col = i + key_co5 + 7;
			i_row = 0;
			shiftPressed = true;
		}
		else if( keyChar == '5' ) {
			pcKeyNextShift7 = Chord.Interval.MAJOR_SEVENTH; return;
		}
		else if( keyChar == '7' ) {
			pcKeyNextShift7 = Chord.Interval.SEVENTH; return;
		}
		// Shift current key-signature
		else if( keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_KP_LEFT ) {
			// Add a flat
			setKeySignature( new Key(key_co5-1) );
			return;
		}
		else if( keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_KP_RIGHT ) {
			// Add a sharp
			setKeySignature( new Key(key_co5+1) );
			return;
		}
		else if( keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_KP_DOWN ) {
			// Semitone down
			Key key = new Key(key_co5);
			key.transpose(-1);
			setKeySignature(key);
			return;
		}
		else if( keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_KP_UP ) {
			// Semitone up
			Key key = new Key(key_co5);
			key.transpose(1);
			setKeySignature(key);
			return;
		}
		if( i < 0 ) // No key char found
			return;
		if( i_col < 0 ) i_col += 12; else if( i_col > N_COLUMNS ) i_col -= 12;
		cl = chordLabels[i_col + N_COLUMNS * i_row];
		chord = cl.chord.clone();
		if( shiftPressed ) {
			chord.set(Chord.Interval.SEVENTH);
		}
		// specify by previous key
		else if( pcKeyNextShift7 == null ) {
			chord.clear(Chord.OffsetIndex.SEVENTH);
		}
		else {
			chord.set(pcKeyNextShift7);
		}
		if( e.isAltDown() ) {
			if( cl.isSus4 ) {
				chord.set(Chord.Interval.MAJOR); // To cancel sus4
				chord.set(Chord.Interval.SHARP5);
			}
			else {
				chord.set(Chord.Interval.FLAT5);
			}
		}
		if( e.isControlDown() ) { // Cannot use for ninth ?
			chord.set(Chord.Interval.NINTH);
		}
		if( selectedChordLabel != null ) clear();
		(selectedChordLabel = cl).setSelection(true);
		setSelectedChord(chord);
		pcKeyNextShift7 = null;
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
	public void fireKeySignatureChanged() {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==ChordMatrixListener.class) {
				((ChordMatrixListener)listeners[i+1]).keySignatureChanged();
			}
		}
	}
	private Key key = null;
	private Key capoKey = null;
	public Key getKeySignature() { return key; }
	public Key getKeySignatureCapo() { return capoKey; }
	public void setKeySignature( Key key ) {
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
			i_color = ((cl.co5Value - key.toCo5() + 31)/3) & 3;
			if( this.key != null ) {
				old_i_color = ((cl.co5Value - this.key.toCo5() + 31)/3) & 3;
				if( i_color != old_i_color ) {
					cl.setBackground(i_color);
				}
			}
			else cl.setBackground(i_color);
			if( !(cl.isSus4) ) {
				if( this.key != null && Music.mod12(cl.co5Value - this.key.toCo5()) == 0)
					cl.setBold(false);
				if( Music.mod12( cl.co5Value - key.toCo5() ) == 0 )
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
		Key prev_key = key;
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
	public void setBeat(SequenceTickIndex sequenceTickIndex) {
		byte beat = (byte)(sequenceTickIndex.lastBeat);
		byte tsu = sequenceTickIndex.timesigUpper;
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
	private Chord	selectedChord = null;
	public Chord getSelectedChord() {
		return selectedChord;
	}
	private Chord	selectedChordCapo = null;
	public Chord getSelectedChordCapo() {
		return selectedChordCapo;
	}
	public void setSelectedChordCapo( Chord chord ) {
		setNoteIndex(-1); // Cancel arpeggio mode
		selectedChord = (chord == null ? null : chord.clone().transpose(-capo,capoKey));
		selectedChordCapo = chord;
		fireChordChanged();
	}
	public void setSelectedChord( Chord chord ) {
		setNoteIndex(-1); // Cancel arpeggio mode
		selectedChord = chord;
		selectedChordCapo = (chord == null ? null : chord.clone().transpose(capo,key));
		fireChordChanged();
	}
	/**
	 * コードを文字列で設定します。
	 * @param chordSymbol コード名
	 */
	public void setSelectedChord(String chordSymbol) throws IllegalArgumentException {
		Chord chord = null;
		if( chordSymbol != null && ! chordSymbol.isEmpty() ) {
			try {
				chord = new Chord(chordSymbol);
			} catch( IllegalArgumentException e ) {
				JOptionPane.showMessageDialog(
					null, e.getMessage(), "Input error",
					JOptionPane.ERROR_MESSAGE
				);
				return;
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
