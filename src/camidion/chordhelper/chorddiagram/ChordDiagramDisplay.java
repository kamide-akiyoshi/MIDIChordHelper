package camidion.chordhelper.chorddiagram;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.LinkedList;

import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JComponent;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import camidion.chordhelper.chorddiagram.ChordDiagram.Instrument;
import camidion.chordhelper.music.Chord;
import camidion.chordhelper.music.Music;
import camidion.chordhelper.music.NoteSymbol;

/**
 * コードダイアグラム表示部
 */
class ChordDiagramDisplay extends JComponent
	implements MouseListener, MouseMotionListener {
	/**
	 * 可視フレット数
	 */
	public static final int VISIBLE_FRETS = 4;
	/**
	 * 最大フレット数
	 */
	public static final int MAX_FRETS = 16;
	/**
	 * 弦の最大本数
	 */
	public static final int MAX_STRINGS = 6;
	/**
	 * 左マージン
	 */
	public static final int LEFT_MARGIN_WIDTH = 10;
	/**
	 * 右マージン
	 */
	public static final int RIGHT_MARGIN_WIDTH = 10;
	/**
	 * 上マージン
	 */
	public static final int UPPER_MARGIN_WIDTH = 5;
	/**
	 * 下マージン
	 */
	public static final int LOWER_MARGIN_WIDTH = 5;
	/**
	 * フレット方向の横スクロールバーで使用する境界つき値範囲
	 */
	DefaultBoundedRangeModel fretViewIndexModel
		= new DefaultBoundedRangeModel( 0, VISIBLE_FRETS, 0, MAX_FRETS );
	/**
	 * チューニング対象楽器
	 */
	private Instrument targetInstrument;
	/**
	 * 開放弦チューニング音階（ずらしあり）
	 */
	private int[] notesWhenOpen;
	/**
	 * コードの押さえ方のバリエーション
	 */
	ChordVariations chordVariations = new ChordVariations();
	/**
	 * チューニングボタンの配列
	 */
	private TuningButton tuningButtons[];
	/**
	 * チューニングボタン
	 */
	private class TuningButton extends Rectangle {
		boolean isMouseEntered = false;
		int stringIndex;
		public TuningButton(int stringIndex) {
			super(
				LEFT_MARGIN_WIDTH,
				UPPER_MARGIN_WIDTH + stringIndex * stringDistance,
				CHAR_WIDTH,
				stringDistance
			);
			this.stringIndex = stringIndex;
		}
	}
	/**
	 * 押さえる場所
	 */
	private class PressingPoint {
		/**
		 * 弦インデックス（0始まり）
		 */
		int stringIndex;
		/**
		 * フレットインデックス（0が開放弦、-1が弾かない弦）
		 */
		int fretIndex;
		/**
		 * コード構成音インデックス（0でルート音）
		 */
		int chordNoteIndex;
		/**
		 * 押さえる場所を示す矩形領域
		 */
		Rectangle rect = null;
		/**
		 * マウスカーソルが入ったらtrue
		 */
		boolean isMouseEntered = false;
		/**
		 * 指定した弦を弾かないことを表す {@link PressingPoint} を構築します。
		 * @param stringIndex 弦インデックス
		 */
		public PressingPoint(int stringIndex) {
			this(-1,-1,stringIndex);
		}
		/**
		 * 指定した弦、フレットを押さえると
		 * 指定されたコード構成音が鳴ることを表す {@link PressingPoint} を構築します。
		 * @param fretIndex フレットインデックス
		 * @param chordNoteIndex コード構成音インデックス
		 * @param stringIndex 弦インデックス
		 */
		public PressingPoint(int fretIndex, int chordNoteIndex, int stringIndex) {
			rect = new Rectangle(
				gridRect.x + (
					fretIndex<1 ?
						-(pointSize + 3) :
						(fretIndex * fretDistance - pointSize/2 - fretDistance/2)
				),
				gridRect.y - pointSize/2 + stringIndex * stringDistance,
				pointSize,
				pointSize
			);
			this.fretIndex = fretIndex;
			this.chordNoteIndex = chordNoteIndex;
			this.stringIndex = stringIndex;
		}
	}
	/**
	 * 押さえる場所リスト（配列要素として使えるようにするための空の継承クラス）
	 */
	private class PressingPointList extends LinkedList<PressingPoint> {
	}
	/**
	 * コードの押さえ方のバリエーション
	 */
	class ChordVariations extends LinkedList<PressingPoint[]> {
		/**
		 * 対象コード
		 */
		public Chord chord = null;
		/**
		 * 省略しないコード構成音が全部揃ったかをビットで確認するための値
		 */
		private int checkBitsAllOn = 0;
		/**
		 * 五度の構成音を省略できるコードのときtrue
		 */
		private boolean fifthOmittable = false;
		/**
		 * ルート音を省略できるコードのときtrue
		 */
		private boolean rootOmittable = false;
		/**
		 * バリエーションインデックスの範囲を表すモデル
		 */
		public DefaultBoundedRangeModel indexModel = new DefaultBoundedRangeModel(0,0,0,0);
		/**
		 * コード（和音）を設定します。
		 *
		 * 設定すると、そのコードの押さえ方がくまなく探索され、
		 * バリエーションが再構築されます。
		 *
		 * @param chord コード
		 */
		public void setChord(Chord chord) {
			clear();
			if( (this.chord = chord) == null ) {
				possiblePressingPoints = null;
				indexModel.setRangeProperties(0,0,0,0,false);
				return;
			}
			int chordNoteCount = chord.numberOfNotes();
			rootOmittable = ( chordNoteCount == 5 );
			fifthOmittable = ( chord.symbolSuffix().equals("7") || rootOmittable );
			checkBitsAllOn = (1 << chordNoteCount) - 1;
			possiblePressingPoints = new PressingPointList[notesWhenOpen.length];
			for( int stringIndex=0; stringIndex<possiblePressingPoints.length; stringIndex++ ) {
				possiblePressingPoints[stringIndex] = new PressingPointList();
				for(
					int fretIndex=0;
					fretIndex <= fretViewIndexModel.getValue() + fretViewIndexModel.getExtent();
					fretIndex++
				) {
					if( fretIndex == 0 || fretIndex > fretViewIndexModel.getValue() ) {
						int chordNoteIndex = chord.indexOf(
							notesWhenOpen[stringIndex]+fretIndex
						);
						if( chordNoteIndex >= 0 ) {
							possiblePressingPoints[stringIndex].add(
								new PressingPoint(fretIndex,chordNoteIndex,stringIndex)
							);
						}
					}
				}
				// 'x'-marking string
				possiblePressingPoints[stringIndex].add(new PressingPoint(stringIndex));
			}
			validatingPoints = new PressingPoint[notesWhenOpen.length];
			scanFret(0);
			indexModel.setRangeProperties(-1,1,-1,size(),false);
		}
		/**
		 * 押さえる可能性のある場所のリスト
		 */
		private PressingPointList possiblePressingPoints[] = null;
		/**
		 * 押さえる可能性のある場所のリストを返します。
		 * @return 押さえる可能性のある場所のリスト
		 */
		public PressingPointList[] getPossiblePressingPoints() {
			return possiblePressingPoints;
		}
		/**
		 * 検証対象の押さえ方
		 */
		private PressingPoint validatingPoints[] = null;
		/**
		 * 引数で指定された弦のフレットをスキャンします。
		 *
		 * <p>指定された弦について、
		 * コード構成音のどれか一つを鳴らすことのできる
		 * フレット位置を順にスキャンし、
		 * 新しい押さえ方にフレット位置を記録したうえで、
		 * 次の弦について再帰呼び出しを行います。
		 * </p>
		 * <p>最後の弦まで達すると（再起呼び出しツリーの葉）、
		 * その押さえ方でコード構成音が十分に揃うかどうか検証されます。
		 * 検証結果がOKだった場合、押さえ方がバリエーションリストに追加されます。
		 * </p>
		 *
		 * @param stringIndex 弦インデックス
		 */
		private void scanFret(int stringIndex) {
			int endOfStringIndex = validatingPoints.length - 1;
			for( PressingPoint pp : possiblePressingPoints[stringIndex] ) {
				validatingPoints[stringIndex] = pp;
				if( stringIndex < endOfStringIndex ) {
					scanFret( stringIndex + 1 );
					continue;
				}
				if( hasValidNewVariation() ) {
					add(validatingPoints.clone());
				}
			}
		}
		/**
		 * 新しい押さえ方のバリエーションが、
		 * そのコードを鳴らすのに十分であるか検証します。
		 *
		 * @return 省略しないコード構成音が全部揃っていたらtrue、
		 * 一つでも欠けていたらfalse
		 */
		private boolean hasValidNewVariation() {
			int checkBits = 0;
			int iocn;
			for( PressingPoint pp : validatingPoints )
				if( (iocn = pp.chordNoteIndex) >= 0 ) checkBits |= 1 << iocn;
			return ( checkBits == checkBitsAllOn
				|| checkBits == checkBitsAllOn -4 && fifthOmittable
				|| (checkBits & (checkBitsAllOn -1-4)) == (checkBitsAllOn -1-4)
				&& (checkBits & (1+4)) != 0 && rootOmittable
			);
		}
		/**
		 * バリエーションインデックスの説明を返します。
		 *
		 * <p>(インデックス値 / バリエーションの個数) のような説明です。
		 * インデックス値が未選択（-1）の場合、
		 * バリエーションの個数のみの説明を返します。
		 * </p>
		 * @return バリエーションインデックスの説明
		 */
		public String getIndexDescription() {
			if( chord == null )
				return null;
			int val = indexModel.getValue();
			int max = indexModel.getMaximum();
			if( val < 0 ) { // 未選択時
				switch(max) {
				case 0: return "No variation found";
				case 1: return "1 variation found";
				default: return max + " variations found";
				}
			}
			return "Variation: " + (val+1) + " / " + max ;
		}
	}

	public ChordDiagramDisplay(ChordDiagram.Instrument inst) {
		addMouseListener(this);
		addMouseMotionListener(this);
		addComponentListener(
			new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent e) {
					tune();
				}
			}
		);
		chordVariations.indexModel.addChangeListener(
			new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					repaint();
				}
			}
		);
		fretViewIndexModel.addChangeListener(
			new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					setChord(); // To reconstruct chord variations
				}
			}
		);
		setMinimumSize(new Dimension(100,70));
		tune(inst);
	}
	@Override
	public void paint(Graphics g) {
		Graphics2D g2 = (Graphics2D) g;
		Dimension d = getSize();
		Color fret_color = Color.gray; // getBackground().darker();
		FontMetrics fm = g2.getFontMetrics();
		//
		// Copy background color
		g2.setBackground(getBackground());
		g2.clearRect(0, 0, d.width, d.height);
		//
		// Draw frets and its numbers
		//
		for( int i=1; i<=VISIBLE_FRETS; i++ ) {
			g2.setColor(fret_color);
			int fret_x = gridRect.x + (gridRect.width - 2) * i / VISIBLE_FRETS;
			g2.drawLine(
				fret_x, gridRect.y,
				fret_x, gridRect.y + stringDistance * (notesWhenOpen.length - 1)
			);
			g2.setColor(getForeground());
			String s = String.valueOf( i + fretViewIndexModel.getValue() );
			g2.drawString(
				s,
				gridRect.x
				+ fretDistance/2 - fm.stringWidth(s)/2
				+ gridRect.width * (i-1) / VISIBLE_FRETS,
				gridRect.y
				+ stringDistance/2 + fm.getHeight()
				+ stringDistance * (notesWhenOpen.length - 1) - 1
			);
		}
		//
		// Draw strings and open notes
		for( int i=0; i<notesWhenOpen.length; i++ ) {
			int string_y = gridRect.y + gridRect.height * i / (MAX_STRINGS - 1);
			g2.setColor(fret_color);
			g2.drawLine(
				gridRect.x,
				string_y,
				gridRect.x + (gridRect.width - 2),
				string_y
			);
			if( notesWhenOpen[i] != targetInstrument.getDefaultOpenNotes().get(i) ) {
				g2.setColor(Color.yellow);
				g2.fill(tuningButtons[i]);
			}
			g2.setColor(getForeground());
			g2.drawString(
				NoteSymbol.noteNumberToSymbol(notesWhenOpen[i], 2),
				LEFT_MARGIN_WIDTH,
				string_y + (fm.getHeight() - fm.getDescent())/2
			);
			g2.setColor(fret_color);
			if( tuningButtons[i].isMouseEntered ) {
				g2.draw(tuningButtons[i]);
			}
		}
		//
		// Draw left-end of frets
		if( fretViewIndexModel.getValue() == 0 ) {
			g2.setColor(getForeground());
			g2.fillRect(
				gridRect.x - 1,
				gridRect.y,
				3,
				stringDistance * (notesWhenOpen.length - 1) + 1
			);
		}
		else {
			g2.setColor(fret_color);
			g2.drawLine(
				gridRect.x,
				gridRect.y,
				gridRect.x,
				gridRect.y + stringDistance * (notesWhenOpen.length - 1)
			);
		}
		//
		// Draw indicators
		if( chordVariations.chord == null ) {
			return;
		}
		PressingPoint variation[] = null;
		int ppIndex = chordVariations.indexModel.getValue();
		if( ppIndex >= 0 ) {
			variation = chordVariations.get(ppIndex);
			for( PressingPoint pp : variation ) drawIndicator(g2, pp, false);
		}
		PressingPointList possiblePressingPoints[] = chordVariations.getPossiblePressingPoints();
		if( possiblePressingPoints != null ) {
			for( PressingPointList pps : possiblePressingPoints ) {
				for( PressingPoint pp : pps ) {
					if( pp.isMouseEntered ) {
						drawIndicator( g2, pp, false );
						if( variation != null ) {
							return;
						}
					}
					else if( variation == null ) {
						drawIndicator( g2, pp, true );
					}
				}
			}
		}
	}
	private void drawIndicator(
		Graphics2D g2, PressingPoint pp, boolean drawAllPoints
	) {
		Rectangle r;
		int i_chord = pp.chordNoteIndex;
		g2.setColor(
			i_chord < 0 ? getForeground() : Chord.NOTE_INDEX_COLORS[i_chord]
		);
		if( (r = pp.rect) == null ) {
			return;
		}
		int fretPoint = pp.fretIndex;
		if( fretPoint < 0 ) {
			if( ! drawAllPoints ) {
				// Put 'x' mark
				g2.drawLine(
					r.x + 1,
					r.y + 1,
					r.x + r.width - 1,
					r.y + r.height - 1
				);
				g2.drawLine(
					r.x + 1,
					r.y + r.height - 1,
					r.x + r.width - 1,
					r.y + 1
				);
			}
		}
		else if( fretPoint == 0 ) {
			// Put 'o' mark
			g2.drawOval( r.x, r.y, r.width, r.height );
		}
		else { // Fret-pressing
			int x = r.x - fretViewIndexModel.getValue() * fretDistance ;
			if( drawAllPoints ) {
				g2.drawOval( x, r.y, r.width, r.height );
			}
			else {
				g2.fillOval( x, r.y, r.width, r.height );
			}
		}
	}
	@Override
	public void mousePressed(MouseEvent e) {
		Point point = e.getPoint();
		PressingPointList possiblePressingPoints[] = chordVariations.getPossiblePressingPoints();
		if( possiblePressingPoints != null ) {
			for( PressingPointList pps : possiblePressingPoints ) {
				for( PressingPoint pp : pps ) {
					boolean hit;
					Rectangle rect = pp.rect;
					if( pp.fretIndex > 0 ) {
						int xOffset = -fretViewIndexModel.getValue()*fretDistance;
						rect.translate( xOffset, 0 );
						hit = rect.contains(point);
						rect.translate( -xOffset, 0 );
					}
					else hit = rect.contains(point);
					if( ! hit )
						continue;
					int variationIndex = 0;
					for( PressingPoint[] variation : chordVariations ) {
						if( variation[pp.stringIndex].fretIndex != pp.fretIndex ) {
							variationIndex++;
							continue;
						}
						chordVariations.indexModel.setValue(variationIndex);
						return;
					}
				}
			}
		}
		for( TuningButton button : tuningButtons ) {
			if( ! button.contains(point) )
				continue;
			int note = notesWhenOpen[button.stringIndex];
			note += (e.getButton()==MouseEvent.BUTTON3 ? 11 : 1);
			notesWhenOpen[button.stringIndex] = Music.mod12(note);
			setChord();
			return;
		}
	}
	@Override
	public void mouseReleased(MouseEvent e) { }
	@Override
	public void mouseEntered(MouseEvent e) {
		mouseMoved(e);
	}
	@Override
	public void mouseExited(MouseEvent e) {
		mouseMoved(e);
	}
	@Override
	public void mouseClicked(MouseEvent e) { }
	@Override
	public void mouseDragged(MouseEvent e) {
	}
	@Override
	public void mouseMoved(MouseEvent e) {
		Point point = e.getPoint();
		boolean changed = false;
		boolean hit;
		for( TuningButton button : tuningButtons ) {
			hit = button.contains(point);
			if ( button.isMouseEntered != hit ) changed = true;
			button.isMouseEntered = hit;
		}
		PressingPointList possible_points[] = chordVariations.getPossiblePressingPoints();
		if( possible_points != null ) {
			for( PressingPointList pps : possible_points ) {
				for( PressingPoint pp : pps ) {
					if( pp.fretIndex > 0 ) {
						int xOffset = -fretViewIndexModel.getValue()*fretDistance;
						pp.rect.translate( xOffset, 0 );
						hit = pp.rect.contains(point);
						pp.rect.translate( -xOffset, 0 );
					}
					else hit = pp.rect.contains(point);
					if ( pp.isMouseEntered != hit ) changed = true;
					pp.isMouseEntered = hit;
				}
			}
		}
		if( changed ) repaint();
	}
	private static final int CHAR_WIDTH = 8; // FontMetrics.stringWidth("C#");
	private static final int CHAR_HEIGHT = 16; // FontMetrics.getHeight();
	/**
	 * 弦間距離（リサイズによって変化する）
	 */
	private int stringDistance;
	/**
	 * フレット間距離（リサイズによって変化する）
	 */
	private int fretDistance;
	/**
	 * 押さえるポイントの直径（リサイズによって変化する）
	 */
	private int pointSize;
	/**
	 * 可視部分の矩形（リサイズによって変化する）
	 */
	private Rectangle gridRect;
	/**
	 * 指定された楽器用にチューニングをリセットします。
	 * @param inst 対象楽器
	 */
	public void tune(ChordDiagram.Instrument inst) {
		notesWhenOpen = (targetInstrument = inst).createTunableOpenNotes();
		tune();
	}
	/**
	 * チューニングを行います。
	 */
	public void tune() {
		Dimension sz = getSize();
		stringDistance = (
			sz.height + 1
			- UPPER_MARGIN_WIDTH - LOWER_MARGIN_WIDTH
			- CHAR_HEIGHT
		) / MAX_STRINGS;

		pointSize = stringDistance * 4 / 5;

		fretDistance = (
			sz.width + 1 - (
				LEFT_MARGIN_WIDTH + RIGHT_MARGIN_WIDTH
				+ CHAR_WIDTH + pointSize + 8
			)
		) / VISIBLE_FRETS;

		gridRect = new Rectangle(
			LEFT_MARGIN_WIDTH + pointSize + CHAR_WIDTH + 8,
			UPPER_MARGIN_WIDTH + stringDistance / 2,
			fretDistance * VISIBLE_FRETS,
			stringDistance * (MAX_STRINGS - 1)
		);

		tuningButtons = new TuningButton[targetInstrument.getDefaultOpenNotes().size()];
		for( int i=0; i<tuningButtons.length; i++ ) {
			tuningButtons[i] = new TuningButton(i);
		}
		setChord();
	}
	/**
	 * コード（和音）を再設定します。
	 */
	public void setChord() {
		setChord(chordVariations.chord);
	}
	/**
	 * コード（和音）を設定します。
	 * @param chord コード
	 */
	public void setChord(Chord chord) {
		chordVariations.setChord(chord);
		repaint();
	}
}