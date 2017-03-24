package camidion.chordhelper.anogakki;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Innocence「あの楽器」風の表示を行う拡張クラスです。
 *
 * <p>{@link #start()} メソッドで表示を開始でき、
 * 時間が経過すると表示が自然に消えるようになっています。
 * </p>
 * <p>画面を「あの楽器」化するには、その画面とともに
 * このクラスのインスタンスを {@link JLayeredPane} で重ね合わせます。
 * </p>
 */
public class AnoGakkiPane extends JComponent {
	/**
	 * １ステップあたりの時間間隔（ミリ秒）
	 */
	private static final int INTERVAL_MS = 15;
	/**
	 * 表示終了までのステップ数
	 */
	private static final int INITIAL_COUNT = 20;
	/**
	 * 角速度ωの（絶対値の）最大
	 */
	private static final double MAX_OMEGA = 0.005;
	/**
	 * 図形の種類
	 */
	private static enum Shape {
		/** ○ */
		CIRCLE {
			public void draw(Graphics2D g2, QueueEntry entry) {
				entry.drawCircle(g2);
			}
		},
		/** ＝ */
		LINES {
			public void draw(Graphics2D g2, QueueEntry entry) {
				entry.drawLines(g2);
			}
		},
		/** □ */
		SQUARE {
			public void draw(Graphics2D g2, QueueEntry entry) {
				entry.drawSquare(g2);
			}
		},
		/** △ */
		TRIANGLE {
			public void draw(Graphics2D g2, QueueEntry entry) {
				entry.drawTriangle(g2);
			}
		};
		/**
		 * 図形の種類の値をランダムに返します。
		 * @return ランダムな値
		 */
		public static Shape randomShape() {
			return values()[(int)(Math.random() * values().length)];
		}
		/**
		 * この図形を描画します。
		 * @param g2 描画オブジェクト
		 * @param entry キューエントリ
		 */
		public abstract void draw(Graphics2D g2, QueueEntry entry);
	}
	/**
	 * 色（RGBA、Aは「不透明度」を表すアルファ値）
	 */
	private static final Color color = new Color(0,255,255,192);
	/**
	 * 線の太さ
	 */
	private static final Stroke stroke = new BasicStroke((float)5);
	/**
	 * いま描画すべき図形を覚えておくためのキュー
	 */
	private List<QueueEntry> queue = new LinkedList<QueueEntry>();
	/**
	 * キューエントリ内容
	 */
	private class QueueEntry {
		/** 時間軸 */
		private int countdown = INITIAL_COUNT;
		/** スタートからの経過時間（ミリ秒） */
		private int tms = 0;
		//
		/** 図形の種類 */
		AnoGakkiPane.Shape shape = Shape.randomShape();
		/** クリックされた場所（中心） */
		private Point clickedPoint;
		//
		// 回転する場合
		/** 現在の半径 */
		private int r = 0;
		/** 回転速度（時計回り） */
		private double omega = 0;
		/** 現在の回転角（アフィン変換） */
		AffineTransform affineTransform = null;
		/**
		 * 新しいキューエントリを構築します。
		 * @param clickedPoint クリックされた場所
		 */
		public QueueEntry(Point clickedPoint) {
			this.clickedPoint = clickedPoint;
			if( shape != Shape.CIRCLE ) {
				// ○以外なら回転角を初期化
				//（○は回転しても見かけ上何も変わらなくて無駄なので除外）
				affineTransform = AffineTransform.getRotateInstance(
					2 * Math.PI * Math.random(),
					clickedPoint.x,
					clickedPoint.y
				);
				omega = MAX_OMEGA * (1.0 - 2.0 * Math.random());
			}
		}
		/**
		 * このキューエントリをカウントダウンします。
		 * @return カウントダウン値（0 でタイムアウト）
		 */
		public int countDown() {
			if( countdown > 0 ) {
				// 時間 t を進める
				countdown--;
				tms += INTERVAL_MS;
				// 半径 r = vt
				r = tms / 2;
				// 回転
				if( shape == Shape.SQUARE || shape == Shape.TRIANGLE ) {
					// 角度を θ=ωt で求めると、移動距離 l=rθ が
					// t の２乗のオーダーで伸びるため、加速しているように見えてしまう。
					// 一定の速度に見せるために t を平方根にして角度を計算する。
					affineTransform.rotate(
						omega * Math.sqrt((double)tms),
						clickedPoint.x,
						clickedPoint.y
					);
				}
			}
			return countdown;
		}
		/**
		 * ○を描画します。
		 * @param g2 描画オブジェクト
		 */
		public void drawCircle(Graphics2D g2) {
			int d = 2 * r;
			g2.drawOval( clickedPoint.x-r, clickedPoint.y-r, d, d );
		}
		/**
		 * ＝を描画します。
		 * @param g2 描画オブジェクト
		 */
		public void drawLines(Graphics2D g2) {
			int width2 = 2 * getSize().width;
			int y = clickedPoint.y;
			g2.transform(affineTransform);
			g2.drawLine( -width2, y-r, width2, y-r );
			g2.drawLine( -width2, y+r, width2, y+r );
		}
		/**
		 * □を描画します。
		 * @param g2 描画オブジェクト
		 */
		public void drawSquare(Graphics2D g2) {
			int d = 2 * r;
			g2.transform(affineTransform);
			g2.drawRect( clickedPoint.x-r, clickedPoint.y-r, d, d );
		}
		/**
		 * △を描画します。
		 * @param g2 描画オブジェクト
		 */
		public void drawTriangle(Graphics2D g2) {
			int x = clickedPoint.x;
			int y = clickedPoint.y;
			g2.transform(affineTransform);
			g2.drawLine( x-r, y, x+r, y-r );
			g2.drawLine( x-r, y, x+r, y+r );
			g2.drawLine( x+r, y-r, x+r, y+r );
		}
	}
	/**
	 * キューを更新するアニメーション用タイマー
	 */
	public AnoGakkiPane() {
		setOpaque(false);
		timer = new Timer(INTERVAL_MS, e->{
			synchronized(queue) {
				Iterator<QueueEntry> i = queue.iterator();
				while(i.hasNext()) if(i.next().countDown() <= 0) i.remove();
			}
			if(queue.isEmpty()) timer.stop();
			repaint();
		}) {
			{setCoalesce(true);setRepeats(true);}
		};
	}
	private Timer timer;
	@Override
	public void paint(Graphics g) {
		if(queue.isEmpty()) return;
		Graphics2D g2 = (Graphics2D)g;
		g2.setStroke(stroke);
		g2.setColor(color);
		synchronized(queue) { queue.forEach(e -> e.shape.draw(g2, e)); }
	}
	/**
	 * 指定された長方形領域（{@link Rectangle}）の中央から図形の表示を開始します。
	 * @param source AWTコンポーネント
	 * @param rect AWTコンポーネント内の座標系を基準とした長方形領域
	 */
	public void start(Component source, Rectangle rect) {
		Point point = rect.getLocation();
		point.translate( rect.width/2, rect.height/2 );
		start(source, point);
	}
	private long prevStartedAt = System.nanoTime();
	/**
	 * 指定された場所から図形の表示を開始します。
	 * @param source AWTコンポーネント
	 * @param point AWTコンポーネント内の座標系を基準とした場所
	 */
	public void start(Component source, Point point) {
		long startedAt = System.nanoTime();
		if( startedAt - prevStartedAt < (INTERVAL_MS * 1000)*50 ) {
			// 頻繁すぎる場合は無視する
			return;
		}
		point = SwingUtilities.convertPoint(source, point, this);
		synchronized(queue) { queue.add(new QueueEntry(point)); }
		timer.start();
		prevStartedAt = startedAt;
	}
}