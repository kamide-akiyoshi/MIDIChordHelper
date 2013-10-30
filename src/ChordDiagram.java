import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollBar;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * ChordDiagram class for MIDI Chord Helper
 *
 * @auther
 *	Copyright (C) 2004-2013 Akiyoshi Kamide
 *	http://www.yk.rim.or.jp/~kamide/music/chordhelper/
 */
public class ChordDiagram extends JPanel {
	/**
	 * コードダイヤグラムの対象楽器
	 */
	public enum TargetInstrument {
		/** ウクレレ  */
		Ukulele(Arrays.asList(9,4,0,7),true), // AECG
		/** ギター */
		Guitar(Arrays.asList(4,11,7,2,9,4),false); // EBGDAE
		/**
		 * 開放弦の音階を表す、変更不可能なノート番号リストを返します。
		 * @return 開放弦の音階（固定値）を表すノート番号リスト
		 */
		public List<Integer> getOpenNotes() { return openNotes; }
		private List<Integer> openNotes;
		JRadioButton radioButton;
		private TargetInstrument(List<Integer> openNotes, boolean isUkulele) {
			this.openNotes = Collections.unmodifiableList(openNotes);
			radioButton = new JRadioButton(toString(), isUkulele);
			radioButton.setOpaque(false);
		}
		/**
		 * 開放弦の音階を表すノート番号の配列を生成します。
		 * この配列の音階はチューニングのために書き換えることができます。
		 *
		 * @return 開放弦の音階（デフォルト値）を表すノート番号の配列
		 */
		public int[] createTunableOpenNotes() {
			int[] r = new int[openNotes.size()];
			int i=0;
			for(int note : openNotes) r[i++] = note;
			return r;
		}
	}
	/**
	 * コードをテキストに記録するボタン
	 */
	public JToggleButton recordTextButton =
		new JToggleButton("REC", new ButtonIcon(ButtonIcon.REC_ICON)) {
			{
				setMargin(new Insets(0,0,0,0));
				setToolTipText("Record to text ON/OFF");
			}
		};
	/**
	 * コードダイアグラムのタイトルラベル
	 */
	public ChordDisplay titleLabel =
		new ChordDisplay("Chord Diagram",null,null) {
			{
				setHorizontalAlignment(SwingConstants.CENTER);
				setVerticalAlignment(SwingConstants.BOTTOM);
			}
		};
	/**
	 * コードダイアグラム表示部
	 */
	private ChordDiagramDisplay diagramDisplay =
		new ChordDiagramDisplay(TargetInstrument.Ukulele) {
			{
				setOpaque(false);
				setPreferredSize(new Dimension(120,120));
			}
		};
	/**
	 * 対象楽器選択ボタンのマップ
	 */
	private Map<TargetInstrument,JRadioButton> instButtons =
		new EnumMap<TargetInstrument,JRadioButton>(TargetInstrument.class) {
			{
				ButtonGroup buttonGroup = new ButtonGroup();
				for( final TargetInstrument instrument : TargetInstrument.values() ) {
					instrument.radioButton.addActionListener(
						new ActionListener() {
							@Override
							public void actionPerformed(ActionEvent e) {
								diagramDisplay.tune(instrument);
							}
						}
					);
					buttonGroup.add(instrument.radioButton);
					put(instrument, instrument.radioButton);
				}
			}
		};

	private JScrollBar variationScrollbar = new JScrollBar(JScrollBar.VERTICAL) {
		{
			setModel(diagramDisplay.chordVariations.indexModel);
			addAdjustmentListener(
				new AdjustmentListener() {
					@Override
					public void adjustmentValueChanged(AdjustmentEvent e) {
						setToolTipText(
							diagramDisplay.chordVariations.getIndexDescription()
						);
					}
				}
			);
		}
	};
	private JScrollBar fretRangeScrollbar = new JScrollBar(JScrollBar.HORIZONTAL) {
		{
			setModel(diagramDisplay.fretViewIndexModel);
			setBlockIncrement(diagramDisplay.fretViewIndexModel.getExtent());
		}
	};
	/**
	 * カポ位置選択コンボボックス
	 */
	public CapoSelecterView capoSelecterView = new CapoSelecterView() {
		{
			checkbox.addItemListener(
				new ItemListener() {
					@Override
					public void itemStateChanged(ItemEvent e) {
						clear();
					}
				}
			);
		}
	};
	/**
	 * コードダイアグラムを構築します。
	 * @param applet 親となるアプレット
	 */
	public ChordDiagram(ChordHelperApplet applet) {
		capoSelecterView.valueSelecter.setModel(
			applet.chordMatrix.capoValueModel
		);
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.X_AXIS));
		mainPanel.setOpaque(false);
		mainPanel.add(new JPanel() {
			{
				setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
				setOpaque(false);
				add(
					new JPanel() {
						{
							add(titleLabel);
							setOpaque(false);
							setAlignmentY((float)0);
						}
					}
				);
				add(diagramDisplay);
				fretRangeScrollbar.setAlignmentY((float)1.0);
				add(fretRangeScrollbar);
				add(
					new JPanel() {
						{
							setOpaque(false);
							for(JRadioButton rb : instButtons.values())
								add(rb);
							setAlignmentY((float)1.0);
						}
					}
				);
			}
		});
		mainPanel.add(variationScrollbar);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		add(
			new JPanel() {
				{
					setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
					setOpaque(false);
					add(Box.createHorizontalStrut(2));
					add(recordTextButton);
					add(Box.createHorizontalStrut(2));
					add(capoSelecterView);
				}
			}
		);
		add(Box.createHorizontalStrut(5));
		add(mainPanel);
	}
	@Override
	public void setBackground(Color bgColor) {
		super.setBackground(bgColor);
		if( diagramDisplay == null )
			return;
		diagramDisplay.setBackground(bgColor);
		capoSelecterView.setBackground(bgColor);
		capoSelecterView.valueSelecter.setBackground(bgColor);
		variationScrollbar.setBackground(bgColor);
		fretRangeScrollbar.setBackground(bgColor);
	}
	/**
	 * コード（和音）をクリアします。
	 *
	 * <p>{@link #setChord(Chord)} の引数に null を指定して呼び出しているだけです。
	 * </p>
	 */
	public void clear() { setChord(null); }
	/**
	 * コード（和音）を設定します。
	 * 表示中でない場合、指定のコードに関係なく null が設定されます。
	 *
	 * @param chord コード（クリアする場合は null）
	 */
	public void setChord(Music.Chord chord) {
		if( ! isVisible() ) chord = null;
		titleLabel.setChord(chord);
		diagramDisplay.setChord(chord);
	}
	/**
	 * 対象楽器を切り替えます。
	 * @param instrument 対象楽器
	 * @throws NullPointerException 対象楽器がnullの場合
	 */
	public void setTargetInstrument(TargetInstrument instrument) {
		instButtons.get(Objects.requireNonNull(instrument)).doClick();
	}
}

/**
 * カポ選択ビュー
 */
class CapoSelecterView extends JPanel implements ItemListener {
	/**
	 * カポON/OFFチェックボックス
	 */
	public JCheckBox checkbox = new JCheckBox("Capo") {
		{
			setOpaque(false);
		}
	};
	/**
	 * カポ位置選択コンボボックス
	 */
	public JComboBox<Integer> valueSelecter = new JComboBox<Integer>() {
		{
			setMaximumRowCount(12);
			setVisible(false);
		}
	};
	/**
	 * カポ選択ビューを構築します。
	 */
	public CapoSelecterView() {
		checkbox.addItemListener(this);
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(checkbox);
		add(valueSelecter);
	}
	/**
	 * 指定されたデータモデルを操作するカポ選択ビューを構築します。
	 * @param model データモデル
	 */
	public CapoSelecterView(ComboBoxModel<Integer> model) {
		this();
		valueSelecter.setModel(model);
	}
	@Override
	public void itemStateChanged(ItemEvent e) {
		valueSelecter.setVisible(checkbox.isSelected());
	}
	/**
	 * カポ位置を返します。
	 * @return カポ位置
	 */
	public int getCapo() {
		return checkbox.isSelected() ? valueSelecter.getSelectedIndex()+1 : 0;
	}
}

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
	private ChordDiagram.TargetInstrument targetInstrument;
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
	class PressingPointList extends LinkedList<PressingPoint> {
	}
	/**
	 * コードの押さえ方のバリエーション
	 */
	class ChordVariations extends LinkedList<PressingPoint[]> {
		/**
		 * 対象コード
		 */
		public Music.Chord chord = null;
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
		public void setChord(Music.Chord chord) {
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

	public ChordDiagramDisplay(ChordDiagram.TargetInstrument inst) {
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
			if( notesWhenOpen[i] != targetInstrument.getOpenNotes().get(i) ) {
				g2.setColor(Color.yellow);
				g2.fill(tuningButtons[i]);
			}
			g2.setColor(getForeground());
			g2.drawString(
				Music.NoteSymbol.noteNumberToSymbol(notesWhenOpen[i], 2),
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
			i_chord < 0 ? getForeground() : Music.Chord.NOTE_INDEX_COLORS[i_chord]
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
	public void tune(ChordDiagram.TargetInstrument inst) {
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

		tuningButtons = new TuningButton[targetInstrument.getOpenNotes().size()];
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
	public void setChord(Music.Chord chord) {
		chordVariations.setChord(chord);
		repaint();
	}
}
