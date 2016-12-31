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
import java.util.List;

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
	/** 列数 */
	public static final int	N_COLUMNS = Music.SEMITONES_PER_OCTAVE * 2 + 1;
	/** 行数 */
	public static final int	CHORD_BUTTON_ROWS = 3;
	/** 調号ボタン */
	public KeySignatureLabel keysigLabels[] = new KeySignatureLabel[N_COLUMNS];
	/** コードボタン */
	public ChordLabel chordLabels[] = new ChordLabel[N_COLUMNS * CHORD_BUTTON_ROWS];
	/** コードボタンの下のコード表示部 */
	public ChordDisplayLabel chordDisplay = new ChordDisplayLabel("Chord Pad", this, null);

	private NoteWeight noteWeightArray[] = new NoteWeight[Music.SEMITONES_PER_OCTAVE];
	/**
	 * 発音中のノート表示をクリアします。
	 */
	public void clearIndicators() {
		for( int i=0; i<noteWeightArray.length; i++ ) noteWeightArray[i].clear();
		repaint();
	}
	/**
	 * MIDIのノートイベント（ON/OFF）を受け取ります。
	 * @param isNoteOn ONのときtrue
	 * @param noteNumber ノート番号
	 */
	public void note(boolean isNoteOn, int noteNumber) {
		int diff = (isNoteOn ? 1 : -1);
		NoteWeight w = noteWeightArray[Music.mod12(noteNumber)];
		if( noteNumber < 49 ) w.addBass(diff); else w.add(diff);
	}

	/**
	 * 調号ボタン
	 */
	private class KeySignatureLabel extends JLabel {
		public boolean isSelected = false;
		public int co5Value = 0;
		private Color indicatorColor;
		public KeySignatureLabel(int v) {
			Key key = new Key(co5Value = v);
			setOpaque(true);
			setBackground(false);
			setForeground( currentColorset.foregrounds[0] );
			setHorizontalAlignment( JLabel.CENTER );
			String tip = "Key signature: ";
			if(v != key.toCo5()) tip += "out of range" ; else {
				tip += key.signatureDescription() + " " +
					key.toStringIn(NoteSymbol.Language.IN_JAPANESE);
				if( v == 0 ) {
					setIcon(new ButtonIcon(ButtonIcon.NATURAL_ICON));
				}
				else {
					setFont(getFont().deriveFont(Font.PLAIN));
					setText(key.signature());
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
				//
				//  □
				//
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
				// ||
				// ||__||
				//
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
				// ||__
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
			if( co5Value < 0 ) indicatorColor = currentColorset.indicators[2];
			else if( co5Value > 0 ) indicatorColor = currentColorset.indicators[1];
			else indicatorColor = currentColorset.foregrounds[1];
		}
	}
	/**
	 * コードボタン
	 */
	class ChordLabel extends JLabel {
		private byte checkBits = 0;
		private int co5Value;
		private boolean isMinor;
		boolean isSus4;
		private boolean isSelected = false;
		private Chord chord;

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
			if(isMinor) co5Value -= 3;
			String labelText = (isSus4 ? chord.symbolSuffix() : chord.toString());
			Font f = getFont();
			if(!isSus4 && labelText.length() > 3) {
				float smallSize = f.getSize2D() - 2;
				boldFont = f.deriveFont(Font.BOLD, smallSize);
				plainFont = f.deriveFont(Font.PLAIN, smallSize);
			}
			else {
				boldFont = f.deriveFont(Font.BOLD);
				plainFont = f.deriveFont(Font.PLAIN);
			}
			setOpaque(true);
			setBackground(0);
			setForeground(currentColorset.foregrounds[0]);
			setBold(false);
			setHorizontalAlignment(JLabel.CENTER);
			setText(labelText);
			setToolTipText("Chord: " + chord.toName());
		}
		public void paint(Graphics g) {
			super.paint(g);
			Dimension d = getSize();
			Graphics2D g2 = (Graphics2D) g;
			Color color = null;

			if( ! inActiveZone ) g2.setColor( Color.gray );

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
		public void setCheckBit(boolean isOn, int bitIndex) {
			//
			// Check bits: x6x43210
			//   6:BassRoot
			//   4:Augumented5th, 3:Diminished5th, 2:Parfect5th,
			//   1:Major3rd/minor3rd/sus4th, 0:Root
			//
			byte mask = ((byte)(1<<bitIndex));
			byte oldCheckBits = checkBits;
			if(isOn) checkBits |= mask; else checkBits &= ~mask;
			if(oldCheckBits == checkBits) return;
			//
			// Indicator bits: x6543210	6:Bass||_  5:[]  4:+  3:O  2:_ii  1:__  0:||_
			//
			byte indicatorBits = 0;
			if( (checkBits & 1) != 0 ) {
				if( (checkBits & 7) == 7 ) { // All triad notes appared
					//
					// Draw square
					indicatorBits |= 0x20;
					//
					// Draw different-colored vertical lines
					if( indicatorColorIndices[0] != indicatorColorIndices[1] ) {
						indicatorBits |= 1;
					}
					if( indicatorColorIndices[2] != indicatorColorIndices[1] ) {
						indicatorBits |= 4;
					}
				}
				else if( !isSus4 ) {
					//
					// Draw vertical lines  || ii
					indicatorBits |= 5;
					//
					if( (checkBits & 2) != 0 && (!isMinor || (checkBits & 0x18) != 0) ) {
						//
						// Draw horizontal bottom lines __
						indicatorBits |= 2;
					}
				}
				if( !isSus4 ) {
					if( isMinor || (checkBits & 2) != 0 ) {
						indicatorBits |= (byte)(checkBits & 0x18);  // Copy bit 3 and bit 4
					}
					if( (checkBits & 0x40) != 0 ) {
						indicatorBits |= 0x40; // Bass
					}
				}
			}
			if( this.indicatorBits == indicatorBits ) return;
			this.indicatorBits = indicatorBits; repaint();
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
		public void setSelection(boolean isSelected) {
			this.isSelected = isSelected;
			setSelection();
		}
		public void setSelection() {
			setForeground(currentColorset.foregrounds[this.isSelected?1:0]);
		}
		public void setBold(boolean isBold) {
			setFont(isBold ? boldFont : plainFont);
		}
		public void keyChanged() {
			int co5Key = capoKey.toCo5();
			int co5Offset = co5Value - co5Key;
			inActiveZone = (co5Offset <= 6 && co5Offset >= -6) ;
			int rootNote = chord.rootNoteSymbol().toNoteNumber();
			//
			// Reconstruct color index
			//
			// Root
			indicatorColorIndices[0] = Music.isOnScale(rootNote, co5Key) ? 0 : co5Offset > 0 ? 1 : 2;
			//
			// 3rd / sus4
			indicatorColorIndices[1] = Music.isOnScale(rootNote+(isMinor?3:isSus4?5:4), co5Key) ? 0 : co5Offset > 0 ? 1 : 2;
			//
			// P5th
			indicatorColorIndices[2] = Music.isOnScale(rootNote+7, co5Key) ? 0 : co5Offset > 0 ? 1 : 2;
			//
			// dim5th
			indicatorColorIndices[3] = Music.isOnScale(rootNote+6, co5Key) ? 0 : co5Offset > 4 ? 1 : 2;
			//
			// aug5th
			indicatorColorIndices[4] = Music.isOnScale(rootNote+8, co5Key) ? 0 : co5Offset > -3 ? 1 : 2;
		}
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
	 * @param capoComboBoxModel カポ値選択コンボボックスのデータモデル
	 */
	public ChordMatrix(CapoComboBoxModel capoComboBoxModel) {
		capoSelecter = new CapoSelecterView(capoComboBoxModel) {{
			checkbox.addItemListener(new ItemListener() {
				public void itemStateChanged(ItemEvent e) {capoChanged(getCapo());}
			});
			valueSelecter.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {capoChanged(getCapo());}
			});
		}};
		Dimension buttonSize = new Dimension(28,26);
		//
		// Make key-signature labels and chord labels
		KeySignatureLabel l;
		int i, v;
		for (i=0, v= -Music.SEMITONES_PER_OCTAVE; i<N_COLUMNS; i++, v++) {
			l = new KeySignatureLabel(v);
			l.addMouseListener(this);
			l.addMouseMotionListener(this);
			add( keysigLabels[i] = l );
			l.setPreferredSize(buttonSize);
		}
		int row;
		for (i=0; i < N_COLUMNS * CHORD_BUTTON_ROWS; i++) {
			row = i / N_COLUMNS;
			v = i - (N_COLUMNS * row) - 12;
			Chord chord;
			switch(row) {
			case 0: chord = new Chord(new NoteSymbol(v), Chord.Interval.SUS4); break;
			case 2: chord = new Chord(new NoteSymbol(v+3), Chord.Interval.MINOR); break;
			default: chord = new Chord(new NoteSymbol(v)); break;
			}
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
			public void focusGained(FocusEvent e) { repaint(); }
			public void focusLost(FocusEvent e) {
				selectedChord = selectedChordCapo = null;
				fireChordChanged();
				repaint();
			}
		});
		setLayout(new GridLayout( 4, N_COLUMNS, 2, 2 ));
		setKeySignature(new Key());
		//
		// Setup note weight array
		//
		int index;
		List<ChordLabelMarker> markers;
		Chord chord;
		for( int noteNo = 0; noteNo < noteWeightArray.length; noteNo++ ) {
			markers = new ArrayList<ChordLabelMarker>();
			//
			// Root major/minor chords
			for( ChordLabel cl : chordLabels ) {
				if( ! cl.isSus4 && cl.chord.indexOf(noteNo) == 0 ) {
					markers.add(new ChordLabelMarker(cl, 0)); // Root
				}
			}
			// Root sus4 chords
			for( ChordLabel cl : chordLabels ) {
				if( cl.isSus4 && cl.chord.indexOf(noteNo) == 0 ) {
					markers.add(new ChordLabelMarker(cl, 0)); // Root
				}
			}
			// 3rd,sus4th,5th included chords
			for( ChordLabel cl : chordLabels ) {
				index = cl.chord.indexOf(noteNo);
				if( index == 1 || index == 2 ) {
					markers.add(new ChordLabelMarker(cl, index)); // 3rd,sus4,P5
				}
			}
			// Diminished chords (major/minor chord button only)
			for( ChordLabel cl : chordLabels ) {
				if( cl.isSus4 ) continue;
				chord = new Chord(cl.chord, Chord.Interval.FLAT5);
				if( chord.indexOf(noteNo) == 2 ) {
					markers.add(new ChordLabelMarker(cl, 3));
				}
			}
			// Augumented chords (major chord button only)
			for( ChordLabel cl : chordLabels ) {
				if( cl.isSus4 || cl.isMinor ) continue;
				chord = new Chord(cl.chord, Chord.Interval.SHARP5);
				if( chord.indexOf(noteNo) == 2 ) {
					markers.add(new ChordLabelMarker(cl, 4));
				}
			}
			noteWeightArray[noteNo] = new NoteWeight(markers);
		}
	}
	//
	// MouseListener
	public void mousePressed(MouseEvent e) {
		Component obj = e.getComponent();
		if( obj instanceof ChordLabel ) {
			ChordLabel cl = (ChordLabel)obj;
			List<Chord.Interval> intervals = new ArrayList<>(cl.chord.intervals());
			if( (e.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0 ) {
				if( e.isShiftDown() )
					intervals.add(Chord.Interval.MAJOR_SEVENTH);
				else
					intervals.add(Chord.Interval.SEVENTH);
			}
			else if( e.isShiftDown() )
				intervals.add(Chord.Interval.SIXTH);
			if( e.isControlDown() )
				intervals.add(Chord.Interval.NINTH);
			else
				intervals.remove(Chord.Interval.NINTH);

			if( e.isAltDown() ) {
				if( cl.isSus4 ) {
					intervals.add(Chord.Interval.MAJOR); // To cancel sus4
					intervals.add(Chord.Interval.SHARP5);
				}
				else intervals.add(Chord.Interval.FLAT5);
			}
			if( selectedChordLabel != null ) {
				selectedChordLabel.setSelection(false);
			}
			(selectedChordLabel = cl).setSelection(true);
			setSelectedChord(new Chord(cl.chord, intervals));
		}
		else if( obj instanceof KeySignatureLabel ) {
			int v = ((KeySignatureLabel)obj).co5Value;
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
		Component draggedFrom = e.getComponent();
		if( draggedFrom instanceof ChordLabel ) {
			ChordLabel labelDraggedFrom = (ChordLabel)draggedFrom;
			Component draggedTo = getComponentAt(
				labelDraggedFrom.getX() + e.getX(),
				labelDraggedFrom.getY() + e.getY()
			);
			if( draggedTo == this ) return; // Entered to gap between chord buttons
			ChordLabel labelDraggedTo = ((draggedTo instanceof ChordLabel) ? (ChordLabel)draggedTo : null);
			if( labelDraggedTo == labelDraggedFrom ) {
				destinationChordLabel = null; return;
			}
			if( destinationChordLabel != null ) return;
			List<Chord.Interval> intervals = new ArrayList<>(labelDraggedFrom.chord.intervals());
			if( labelDraggedFrom.isMinor ) {
				if( labelDraggedTo == null ) { // Out of chord buttons
					// mM7
					intervals.add(Chord.Interval.MAJOR_SEVENTH);
				}
				else if( labelDraggedFrom.co5Value < labelDraggedTo.co5Value ) { // Right
					// m6
					intervals.add(Chord.Interval.SIXTH);
				}
				else { // Left or up from minor to major
					// m7
					intervals.add(Chord.Interval.SEVENTH);
				}
			}
			else if( labelDraggedFrom.isSus4 ) {
				if( labelDraggedTo == null ) { // Out of chord buttons
					return;
				}
				else if( ! labelDraggedTo.isSus4 ) { // Down from sus4 to major
					intervals.add(Chord.Interval.MAJOR);
				}
				else if( labelDraggedFrom.co5Value < labelDraggedTo.co5Value ) { // Right
					intervals.add(Chord.Interval.NINTH);
				}
				else { // Left
					// 7sus4
					intervals.add(Chord.Interval.SEVENTH);
				}
			}
			else {
				if( labelDraggedTo == null ) { // Out of chord buttons
					return;
				}
				else if( labelDraggedTo.isSus4 ) { // Up from major to sus4
					intervals.add(Chord.Interval.NINTH);
				}
				else if( labelDraggedFrom.co5Value < labelDraggedTo.co5Value ) { // Right
					// M7
					intervals.add(Chord.Interval.MAJOR_SEVENTH);
				}
				else if( labelDraggedTo.isMinor ) { // Down from major to minor
					// 6
					intervals.add(Chord.Interval.SIXTH);
				}
				else { // Left
					// 7
					intervals.add(Chord.Interval.SEVENTH);
				}
			}
			if( intervals.contains(Chord.Interval.NINTH) || (labelDraggedFrom.isSus4 && (labelDraggedTo == null || ! labelDraggedTo.isSus4) ) ) {
				if( (e.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0 ) {
					if( e.isShiftDown() ) {
						intervals.add(Chord.Interval.MAJOR_SEVENTH);
					}
					else {
						intervals.add(Chord.Interval.SEVENTH);
					}
				}
				else if( e.isShiftDown() ) {
					intervals.add(Chord.Interval.SIXTH);
				}
			}
			else {
				if( e.isControlDown() )
					intervals.add(Chord.Interval.NINTH);
				else
					intervals.remove(Chord.Interval.NINTH);
			}
			if( e.isAltDown() ) {
				if( labelDraggedFrom.isSus4 ) {
					intervals.add(Chord.Interval.MAJOR);
					intervals.add(Chord.Interval.SHARP5);
				}
				else {
					intervals.add(Chord.Interval.FLAT5);
				}
			}
			setSelectedChord(new Chord(labelDraggedFrom.chord, intervals));
			destinationChordLabel = (labelDraggedTo == null ? labelDraggedFrom : labelDraggedTo ) ;
		}
		else if( draggedFrom instanceof KeySignatureLabel ) {
			KeySignatureLabel keyDraggedFrom = (KeySignatureLabel)draggedFrom;
			Component draggedTo = getComponentAt(
				keyDraggedFrom.getX() + e.getX(),
				keyDraggedFrom.getY() + e.getY()
			);
			if( !(draggedTo instanceof KeySignatureLabel) ) return;
			KeySignatureLabel keyDraggedTo = (KeySignatureLabel)draggedTo;
			int v = keyDraggedTo.co5Value;
			if( (e.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0 ) {
				v = Music.oppositeCo5(v);
			}
			setKeySignature(new Key(v));
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
		int i = -1, iCol = -1, iRow = 1;
		boolean shiftPressed = false; // True if Shift-key pressed or CapsLocked
		char keyChar = e.getKeyChar();
		int keyCode = e.getKeyCode();
		ChordLabel cl = null;
		Chord chord = null;
		int keyCo5 = key.toCo5();
		// System.out.println( keyChar + " Pressed on chord matrix" );
		//
		if( (i = "6 ".indexOf(keyChar)) >= 0 ) {
			selectedChord = selectedChordCapo = null;
			fireChordChanged();
			pcKeyNextShift7 = null;
			return;
		}
		else if( (i = "asdfghjkl;:]".indexOf(keyChar)) >= 0 ) {
			iCol = i + keyCo5 + 7;
		}
		else if( (i = "ASDFGHJKL+*}".indexOf(keyChar)) >= 0 ) {
			iCol = i + keyCo5 + 7;
			shiftPressed = true;
		}
		else if( (i = "zxcvbnm,./\\".indexOf(keyChar)) >=0 ) {
			iCol = i + keyCo5 + 7;
			iRow = 2;
		}
		else if( (i = "ZXCVBNM<>?_".indexOf(keyChar)) >=0 ) {
			iCol = i + keyCo5 + 7;
			iRow = 2;
			shiftPressed = true;
		}
		else if( (i = "qwertyuiop@[".indexOf(keyChar)) >= 0 ) {
			iCol = i + keyCo5 + 7;
			iRow = 0;
		}
		else if( (i = "QWERTYUIOP`{".indexOf(keyChar)) >= 0 ) {
			iCol = i + keyCo5 + 7;
			iRow = 0;
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
			setKeySignature(new Key(keyCo5 - 1));
			return;
		}
		else if( keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_KP_RIGHT ) {
			// Add a sharp
			setKeySignature(new Key(keyCo5 + 1));
			return;
		}
		else if( keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_KP_DOWN ) {
			// Semitone down
			setKeySignature(new Key(Music.transposeCo5(keyCo5, -1)));
			return;
		}
		else if( keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_KP_UP ) {
			// Semitone up
			setKeySignature(new Key(Music.transposeCo5(keyCo5, 1)));
			return;
		}
		if( i < 0 ) return; // No key char found
		if( iCol < 0 ) iCol += 12; else if( iCol > N_COLUMNS ) iCol -= 12;
		cl = chordLabels[iCol + N_COLUMNS * iRow];
		List<Chord.Interval> intervals = new ArrayList<>(cl.chord.intervals());
		if( shiftPressed ) {
			if( ! intervals.contains(Chord.Interval.SEVENTH) ) {
				intervals.add(Chord.Interval.SEVENTH);
			}
		}
		else if( pcKeyNextShift7 == null ) {
			intervals.remove(Chord.Interval.SEVENTH);
		}
		else intervals.add(pcKeyNextShift7);
		if( e.isAltDown() ) {
			if( cl.isSus4 ) {
				intervals.remove(Chord.Interval.SUS4);
				intervals.add(Chord.Interval.SHARP5);
			}
			else intervals.add(Chord.Interval.FLAT5);
		}
		if( e.isControlDown() ) {
			// TODO: ^Hなど、ここに到達する前に特殊な keyChar がやってきてしまうことがある
			intervals.add(Chord.Interval.NINTH);
		}
		chord = new Chord(cl.chord, intervals);
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
		this.capoKey = (this.key = key).transposedKey(capoSelecter.getCapo());
		for( ChordLabel cl : chordLabels ) cl.keyChanged();
		fireKeySignatureChanged();
	}
	private int capo = 0;
	/**
	 * カポ位置の変更処理
	 * @param newCapo 新しいカポ位置
	 */
	protected void capoChanged(int newCapo) {
		if(capo == newCapo) return;
		capoKey = key.transposedKey(capo = newCapo);
		selectedChordCapo = (selectedChord == null ? null : selectedChord.transposedNewChord(newCapo));
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
	public boolean isDragged() { return destinationChordLabel != null ; }

	private boolean isDark = false;
	public void setDarkMode(boolean isDark) {
		this.isDark = isDark;
		currentColorset = (isDark ? darkModeColorset : normalModeColorset);
		setBackground( currentColorset.focus[0] );
		Key prev_key = key;
		key = null;
		setKeySignature(prev_key);
		for( int i=0; i < keysigLabels.length; i++ ) keysigLabels[i].setSelection();
		for( int i=0; i <  chordLabels.length; i++ ) chordLabels[i].setSelection();
		chordGuide.setDarkMode(isDark);
		chordDisplay.setDarkMode(isDark);
		Color col = isDark ? Color.black : null;
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
		if( currentBeat == beat && timesigUpper == tsu ) return;
		timesigUpper = tsu;
		currentBeat = beat;
		keysigLabels[ key.toCo5() + 12 ].repaint();
	}

	private ChordLabel selectedChordLabel = null;
	public JComponent getSelectedButton() {
		return selectedChordLabel;
	}
	private Chord selectedChord = null;
	public Chord getSelectedChord() {
		return selectedChord;
	}
	private Chord selectedChordCapo = null;
	public Chord getSelectedChordCapo() {
		return selectedChordCapo;
	}
	public void setSelectedChordCapo( Chord chord ) {
		setNoteIndex(-1); // Cancel arpeggio mode
		selectedChord = (chord == null ? null : chord.transposedNewChord(-capo,capoKey));
		selectedChordCapo = chord;
		fireChordChanged();
	}
	public void setSelectedChord( Chord chord ) {
		setNoteIndex(-1); // Cancel arpeggio mode
		selectedChord = chord;
		selectedChordCapo = (chord == null ? null : chord.transposedNewChord(capo,key));
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
				JOptionPane.showMessageDialog(null, e.getMessage(), "Input error", JOptionPane.ERROR_MESSAGE);
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
