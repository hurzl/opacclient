package de.geeksfactory.opacclient.frontend;

import java.io.IOException;
import java.util.List;

import org.json.JSONException;

import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.text.Html;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableLayout.LayoutParams;
import android.widget.TableRow;
import android.widget.TextView;

import com.WazaBe.HoloEverywhere.app.AlertDialog;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import de.geeksfactory.opacclient.OpacClient;
import de.geeksfactory.opacclient.OpacTask;
import de.geeksfactory.opacclient.R;
import de.geeksfactory.opacclient.objects.Account;
import de.geeksfactory.opacclient.objects.Library;

public class AccountActivity extends OpacActivity {

	protected ProgressDialog dialog;

	public static int STATUS_SUCCESS = 0;
	public static int STATUS_NOUSER = 1;
	public static int STATUS_FAILED = 2;

	private LoadTask lt;
	private CancelTask ct;
	private ProlongTask pt;

	private Account account;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		SharedPreferences sp = PreferenceManager
				.getDefaultSharedPreferences(app);

		if (getIntent().getExtras() != null) {
			if (getIntent().getExtras().getLong("notif_last") > 0) {
				SharedPreferences.Editor spe = sp.edit();
				spe.putLong("notification_last", getIntent().getExtras()
						.getLong("notif_last"));
				spe.commit();
				NotificationManager nMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				nMgr.cancel(OpacClient.NOTIF_ID);
			}
		}

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getSupportMenuInflater().inflate(R.menu.activity_account, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			NavUtils.navigateUpFromSameTask(this);
			return true;
		case R.id.action_accounts:
			selectaccount();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onResume() {
		super.onResume();
		setContentView(R.layout.loading);
		((TextView) findViewById(R.id.tvLoading))
				.setText(R.string.loading_account);

		account = ((OpacClient) getApplication()).getAccount();

		if (account.getPassword() == null
				|| account.getPassword().equals("null")
				|| account.getPassword().equals("")) {
			dialog_no_user(true);
		} else {
			lt = new LoadTask();
			lt.execute(app, getIntent().getIntExtra("item", 0));
		}
	}

	protected void cancel(final String a) {

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.cancel_confirm)
				.setCancelable(true)
				.setNegativeButton(R.string.no,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface d, int id) {
								d.cancel();
							}
						})
				.setPositiveButton(R.string.yes,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface d, int id) {
								d.dismiss();
								dialog = ProgressDialog.show(
										AccountActivity.this, "",
										getString(R.string.doing_cancel), true);
								dialog.show();
								ct = new CancelTask();
								ct.execute(app, a);
							}
						})
				.setOnCancelListener(new DialogInterface.OnCancelListener() {
					public void onCancel(DialogInterface d) {
						if (d != null)
							d.cancel();
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

	public void cancel_done(int result) {
		if (result == STATUS_SUCCESS) {
			onResume();
		}
	}

	protected void prolong(final String a) {
		dialog = ProgressDialog.show(AccountActivity.this, "",
				getString(R.string.doing_prolong), true);
		dialog.show();
		pt = new ProlongTask();
		pt.execute(app, a);
	}

	public void prolong_done(int result) {
		if (result == STATUS_SUCCESS) {
			onResume();
		} else if (result == STATUS_FAILED) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(
					"Der Web-Opac meldet: " + app.getApi().getLast_error())
					.setCancelable(false)
					.setNegativeButton(R.string.dismiss,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							});
			AlertDialog alert = builder.create();
			alert.show();
		}
	}

	public class LoadTask extends OpacTask<List<List<String[]>>> {

		private boolean success = true;

		@Override
		protected List<List<String[]>> doInBackground(Object... arg0) {
			super.doInBackground(arg0);
			try {
				List<List<String[]>> res = app.getApi().account(
						((OpacClient) getApplication()).getAccount());
				success = true;
				return res;
			} catch (java.net.UnknownHostException e) {
				publishProgress(e, "ioerror");
			} catch (java.io.IOException e) {
				success = false;
			} catch (de.geeksfactory.opacclient.NotReachableException e) {
				success = false;
			} catch (java.lang.IllegalStateException e) {
				success = false;
			} catch (Exception e) {
				publishProgress(e, "ioerror");
			}
			return null;
		}

		protected void onPostExecute(List<List<String[]>> result) {
			if (success) {
				loaded(result);
			} else {
				setContentView(R.layout.connectivity_error);
				((Button) findViewById(R.id.btRetry))
						.setOnClickListener(new OnClickListener() {
							@Override
							public void onClick(View v) {
								onResume();
							}
						});
			}
		}
	}

	public void loaded(final List<List<String[]>> result) {
		if (result == null) {
			dialog_wrong_credentials(app.getApi().getLast_error(), true);
			return;
		}

		setContentView(R.layout.account_activity);

		((TextView) findViewById(R.id.tvAccHeader)).setText(getString(
				R.string.account_header, account.getLabel()));
		((TextView) findViewById(R.id.tvAccUser)).setText(account.getName());
		TextView tvAccCity = (TextView) findViewById(R.id.tvAccCity);
		Library lib;
		try {
			lib = ((OpacClient) getApplication()).getLibrary(account.getBib());
			if (lib.getTitle() != null && !lib.getTitle().equals("null")) {
				tvAccCity.setText(lib.getCity() + "\n" + lib.getTitle());
			} else {
				tvAccCity.setText(lib.getCity());
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		TableLayout td = (TableLayout) findViewById(R.id.tlMedien);
		td.removeAllViews();
		if (result.get(0).size() == 0) {
			TableRow row = new TableRow(this);
			TextView t1 = new TextView(this);
			t1.setText(R.string.entl_none);
			row.addView(t1);
			td.addView(row, new TableLayout.LayoutParams(
					LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		}
		for (int i = 0; i < result.get(0).size(); i++) {
			TableRow row = new TableRow(this);

			TextView t1 = new TextView(this);
			t1.setText(Html.fromHtml(result.get(0).get(i)[0] + "<br />"
					+ result.get(0).get(i)[1] + "<br />"
					+ result.get(0).get(i)[2]));
			t1.setPadding(0, 0, 10, 10);
			row.addView(t1);

			TextView t2 = new TextView(this);
			t2.setText(Html.fromHtml(result.get(0).get(i)[3] + " ("
					+ result.get(0).get(i)[4] + ")<br />"
					+ result.get(0).get(i)[6]));
			row.addView(t2);

			if (result.get(0).get(i)[7] != null) {
				final int j = i;
				ImageView b1 = new ImageView(this);
				b1.setImageResource(android.R.drawable.ic_input_add);

				b1.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View arg0) {
						AccountActivity.this.prolong(result.get(0).get(j)[7]);
					}
				});
				row.addView(b1);
			}

			td.addView(row, new TableLayout.LayoutParams(
					LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		}

		TableLayout tr = (TableLayout) findViewById(R.id.tlReservations);
		tr.removeAllViews();
		if (result.get(1).size() == 0) {
			TableRow row = new TableRow(this);
			TextView t1 = new TextView(this);
			t1.setText(R.string.reservations_none);
			row.addView(t1);
			tr.addView(row, new TableLayout.LayoutParams(
					LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		}
		for (int i = 0; i < result.get(1).size(); i++) {
			TableRow row = new TableRow(this);

			TextView t1 = new TextView(this);
			t1.setText(Html.fromHtml(result.get(1).get(i)[0] + "<br />"
					+ result.get(1).get(i)[1]));
			t1.setPadding(0, 0, 10, 10);
			row.addView(t1);

			TextView t2 = new TextView(this);
			t2.setText(Html.fromHtml(result.get(1).get(i)[2] + "<br />"
					+ result.get(1).get(i)[3]));
			row.addView(t2);

			if (result.get(1).get(i)[4] != null) {
				final int j = i;
				ImageView b1 = new ImageView(this);
				b1.setImageResource(android.R.drawable.ic_delete);

				b1.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View arg0) {
						cancel(result.get(1).get(j)[4]);
					}
				});
				row.addView(b1);
			}

			tr.addView(row, new TableLayout.LayoutParams(
					LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		}
	}

	public class CancelTask extends OpacTask<Integer> {
		private boolean success = true;

		@Override
		protected Integer doInBackground(Object... arg0) {
			super.doInBackground(arg0);
			String a = (String) arg0[1];
			try {
				app.getApi().cancel(a);
				success = true;
			} catch (java.net.UnknownHostException e) {
				publishProgress(e, "ioerror");
			} catch (java.io.IOException e) {
				success = false;
			} catch (de.geeksfactory.opacclient.NotReachableException e) {
				success = false;
			} catch (java.lang.IllegalStateException e) {
				success = false;
			} catch (Exception e) {
				publishProgress(e, "ioerror");
			}
			return STATUS_SUCCESS;
		}

		protected void onPostExecute(Integer result) {
			dialog.dismiss();

			if (success) {
				cancel_done(result);
			} else {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						AccountActivity.this);
				builder.setMessage(R.string.connection_error)
						.setCancelable(true)
						.setNegativeButton(R.string.dismiss,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int id) {
										dialog.cancel();
									}
								});
				AlertDialog alert = builder.create();
				alert.show();
			}
		}
	}

	public class ProlongTask extends OpacTask<Integer> {
		private boolean success = true;

		@Override
		protected Integer doInBackground(Object... arg0) {
			super.doInBackground(arg0);
			String a = (String) arg0[1];
			try {
				boolean res = app.getApi().prolong(a);
				success = true;
				if (res) {
					return STATUS_SUCCESS;
				} else {
					return STATUS_FAILED;
				}
			} catch (java.net.UnknownHostException e) {
				publishProgress(e, "ioerror");
			} catch (java.io.IOException e) {
				success = false;
			} catch (de.geeksfactory.opacclient.NotReachableException e) {
				success = false;
			} catch (java.lang.IllegalStateException e) {
				success = false;
			} catch (Exception e) {
				publishProgress(e, "ioerror");
			}
			return STATUS_SUCCESS;
		}

		protected void onPostExecute(Integer result) {
			dialog.dismiss();

			if (success) {
				prolong_done(result);
			} else {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						AccountActivity.this);
				builder.setMessage(R.string.connection_error)
						.setCancelable(true)
						.setNegativeButton(R.string.dismiss,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int id) {
										dialog.cancel();
									}
								});
				AlertDialog alert = builder.create();
				alert.show();
			}
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (dialog != null) {
			if (dialog.isShowing()) {
				dialog.cancel();
			}
		}

		try {
			if (lt != null) {
				if (!lt.isCancelled()) {
					lt.cancel(true);
				}
			}
			if (ct != null) {
				if (!ct.isCancelled()) {
					ct.cancel(true);
				}
			}
			if (pt != null) {
				if (!pt.isCancelled()) {
					pt.cancel(true);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
