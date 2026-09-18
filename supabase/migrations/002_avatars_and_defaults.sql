-- Avatars bucket + default avatar for new profiles
-- Run after 001_phase2_social.sql

insert into storage.buckets (id, name, public)
values ('avatars', 'avatars', true)
on conflict (id) do nothing;

drop policy if exists avatars_storage_read on storage.objects;
create policy avatars_storage_read on storage.objects for select to authenticated
  using (bucket_id = 'avatars');

drop policy if exists avatars_storage_write on storage.objects;
create policy avatars_storage_write on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists avatars_storage_update on storage.objects;
create policy avatars_storage_update on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists avatars_storage_delete on storage.objects;
create policy avatars_storage_delete on storage.objects for delete to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

-- Also allow public read if bucket is public
drop policy if exists avatars_storage_public_read on storage.objects;
create policy avatars_storage_public_read on storage.objects for select to anon
  using (bucket_id = 'avatars');

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_name text;
  v_avatar text;
begin
  v_name := coalesce(new.raw_user_meta_data->>'display_name', split_part(new.email, '@', 1), '用户');
  v_avatar := 'https://api.dicebear.com/7.x/thumbs/png?seed=' || new.id::text || '&size=128';
  insert into public.profiles (id, display_name, avatar_url, friend_code)
  values (
    new.id,
    v_name,
    v_avatar,
    public.gen_friend_code()
  )
  on conflict (id) do nothing;
  insert into public.user_settings (user_id, theme_style, custom_moods_json, updated_at)
  values (new.id, 'MALE_BLUE', '[]', now())
  on conflict (user_id) do nothing;
  return new;
end;
$$;
