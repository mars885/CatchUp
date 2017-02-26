package io.sweers.catchup.ui.controllers;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.view.ContextThemeWrapper;
import com.serjltt.moshi.adapters.WrappedJsonAdapter;
import com.squareup.moshi.Moshi;
import dagger.Lazy;
import dagger.Provides;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.sweers.catchup.R;
import io.sweers.catchup.data.EpochInstantJsonAdapter;
import io.sweers.catchup.data.LinkManager;
import io.sweers.catchup.data.medium.MediumService;
import io.sweers.catchup.data.medium.model.Collection;
import io.sweers.catchup.data.medium.model.MediumPost;
import io.sweers.catchup.injection.qualifiers.ForApi;
import io.sweers.catchup.injection.scopes.PerController;
import io.sweers.catchup.rx.autodispose.AutoDispose;
import io.sweers.catchup.ui.activity.ActivityComponent;
import io.sweers.catchup.ui.activity.MainActivity;
import io.sweers.catchup.ui.base.BaseNewsController;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSource;
import org.threeten.bp.Instant;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;

public final class MediumController extends BaseNewsController<MediumPost> {

  @Inject MediumService service;
  @Inject LinkManager linkManager;

  public MediumController() {
    super();
  }

  public MediumController(Bundle args) {
    super(args);
  }

  @Override protected void performInjection() {
    DaggerMediumController_Component.builder()
        .activityComponent(((MainActivity) getActivity()).getComponent())
        .build()
        .inject(this);
  }

  @Override protected Context onThemeContext(@NonNull Context context) {
    return new ContextThemeWrapper(context, R.style.CatchUp_Medium);
  }

  @Override protected void bindItemView(@NonNull MediumPost item, @NonNull ViewHolder holder) {
    holder.title(item.post()
        .title());

    holder.score(Pair.create(
        "\u2665\uFE0E", // Because lol: https://code.google.com/p/android/issues/detail?id=231068
        item.post()
            .virtuals()
            .recommends()));
    holder.timestamp(item.post()
        .createdAt());

    holder.author(item.user()
        .name());

    Collection collection = item.collection();
    if (collection != null) {
      holder.tag(collection.name());
    } else {
      holder.tag(null);
    }

    holder.comments(item.post()
        .virtuals()
        .responsesCreatedCount());
    holder.source(null);

    holder.itemLongClicks()
        .subscribe(AutoDispose.observable(this)
            .around(SmmryController.showFor(this, item.constructUrl())));

    holder.itemClicks()
        .compose(transformUrlToMeta(item.constructUrl()))
        .flatMapCompletable(linkManager)
        .subscribe(AutoDispose.completable(this)
            .empty());
    holder.itemCommentClicks()
        .compose(transformUrlToMeta(item.constructCommentsUrl()))
        .flatMapCompletable(linkManager)
        .subscribe(AutoDispose.completable(this)
            .empty());
  }

  @NonNull @Override protected Single<List<MediumPost>> getDataSingle() {
    return service.top()
        .flatMap(references -> Observable.fromIterable(references.post()
            .values())
            .map(post -> MediumPost.builder()
                .post(post)
                .user(references.user()
                    .get(post.creatorId()))
                .collection(references.collection()
                    .get(post.homeCollectionId()))
                .build()))
        .toList();
  }

  @PerController
  @dagger.Component(modules = Module.class,
                    dependencies = ActivityComponent.class)
  public interface Component {
    void inject(MediumController controller);
  }

  @dagger.Module
  public abstract static class Module {

    @Provides @PerController @ForApi
    static OkHttpClient provideMediumOkHttpClient(OkHttpClient client) {
      return client.newBuilder()
          .addInterceptor(chain -> {
            Request request = chain.request();
            request = request.newBuilder()
                .url(request.url()
                    .newBuilder()
                    .addQueryParameter("format", "json")
                    .build())
                .build();
            Response response = chain.proceed(request);
            BufferedSource source = response.body()
                .source();
            source.skip(source.indexOf((byte) '{'));
            return response;
          })
          .build();
    }

    @Provides @PerController @ForApi static Moshi provideMediumMoshi(Moshi moshi) {
      return moshi.newBuilder()
          .add(Instant.class, new EpochInstantJsonAdapter(TimeUnit.MILLISECONDS))
          .add(WrappedJsonAdapter.FACTORY)
          .build();
    }

    @Provides @PerController
    static MediumService provideMediumService(@ForApi final Lazy<OkHttpClient> client,
        @ForApi Moshi moshi,
        RxJava2CallAdapterFactory rxJavaCallAdapterFactory) {
      Retrofit retrofit = new Retrofit.Builder().baseUrl(MediumService.ENDPOINT)
          .callFactory(request -> client.get()
              .newCall(request))
          .addCallAdapterFactory(rxJavaCallAdapterFactory)
          .addConverterFactory(MoshiConverterFactory.create(moshi))
          .build();
      return retrofit.create(MediumService.class);
    }
  }
}